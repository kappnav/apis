/*
 * Copyright 202 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package application.rest.v1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import javax.inject.Inject;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.namespace.QName;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ibm.kappnav.logging.Logger;

import application.rest.v1.configmaps.ActionConfigMapBuilder;
import application.rest.v1.configmaps.ConfigMapProcessor;
import application.rest.v1.configmaps.KindActionMappingProcessor;
import application.rest.v1.configmaps.OwnerRef;
import application.rest.v1.configmaps.SectionConfigMapBuilder;
import application.rest.v1.configmaps.SectionConfigMapProcessor;
import application.rest.v1.configmaps.StatusMappingConfigMapBuilder;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;

@Path("/kindactionmapping")
@Tag(name = "kindactionmapping", description="kAppNav kindationmapping API")
public class KindActionMappingEndpoint extends KAppNavEndpoint {
    private static final String CLASS_NAME = KindActionMappingEndpoint.class.getName();

    private static final String ACTIONS_CONFIGMAP_TYPE = "actions";
    private static final String STATUS_MAPPING_CONFIGMAP_TYPE = "status-mapping";
    private static final String SECTIONS_CONFIGMAP_TYPE = "sections";
    
    @Inject
    private ComponentInfoRegistry registry;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{resource-kind}")
    @Operation(
            summary = "Computes config maps of a Kubernetes resource.",
            description = "Returns the computed candidate config map name along with the namespaces they are in " + 
                          "and the final configmap content based on the Kubernetes resource information specified."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
                   @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
                   @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
                   @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getConfigmap( 
        @PathParam("resource-kind") @Parameter(description = "The Kubernetes resource kind for the resource") String kind,
        @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("actions") 
                 @QueryParam("type") @Parameter(description = "The type of the configmap to retrieve and the values can be actions, status-mapping, or sections.") String type,
        @Pattern(regexp = API_VERSION_PATTERN_ZERO_OR_MORE) @DefaultValue("v1") @QueryParam("apiversion") @Parameter(description = "The apiVersion of the resource") String apiVersion,
        @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("") @QueryParam("name") @Parameter(description = "The name of the resource") String name,
        @DefaultValue("") @QueryParam("subkind") @Parameter(description = "The subkind of the resource") String subkind,
        @Pattern(regexp = API_VERSION_PATTERN_ZERO_OR_MORE) @DefaultValue("") @QueryParam("owner-apiversion") @Parameter(description = "The owner apiVersion of the resource") String ownerApiVersion,       
        @DefaultValue("") @QueryParam("owner-kind") @Parameter(description = "The owner kind of the resource") String ownerKind,       
        @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("") @QueryParam("owner-uid") @Parameter(description = "The owner Uid of the resource") String ownerUID,       
        @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the resource") String namespace){
        
        try {
            final ApiClient client = getApiClient();
            JsonObject map = null;

            if (registry == null) {
                // Initialize the registry here if CDI failed to do it.
                registry = new ComponentInfoRegistry(client);
            }

            if ((apiVersion == null) || (apiVersion.isEmpty())) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(getStatusMessageAsJSON("No object")).build();
            } else { 
                OwnerRef[] ownerRef = null;
                if (!isOwnerinfoNullOrEmpty(ownerApiVersion, ownerKind, ownerUID)) {
                    ownerRef = new OwnerRef[1];
                    ownerRef[0] = new OwnerRef(ownerApiVersion, ownerKind, ownerUID);
                }

                // Get configmaps available in a cluster
                KindActionMappingProcessor kam =
                    new KindActionMappingProcessor(namespace, ownerRef, apiVersion, name, subkind, kind);
                String configMapName = namespace + "."+ type+ "." + kind.toLowerCase(Locale.ENGLISH)+ 
                                       ConfigMapProcessor.getConfigMapNameSuffix(name, subkind);

                ArrayList <QName> configMapList = null;
                if (type.equals(ACTIONS_CONFIGMAP_TYPE)) {
                    // get configmap names declared in the KindActionMapping custom resources
                    configMapList = kam.getConfigMapsFromKAMs(client, ConfigMapProcessor.ConfigMapType.ACTION, configMapName);

                    ConfigMapProcessor cmProc = new ConfigMapProcessor(kind);
                    map = cmProc.getConfigMap(client, kam, namespace, ConfigMapProcessor.ConfigMapType.ACTION, configMapName, new ActionConfigMapBuilder());
                } else if (type.equals(STATUS_MAPPING_CONFIGMAP_TYPE)) {
                    ConfigMapProcessor cmProc = new ConfigMapProcessor(kind);
                    StatusMappingConfigMapBuilder builder = new StatusMappingConfigMapBuilder();
                   
                    // get configmap names declared in the KindActionMapping custom resources
                    configMapList = kam.getConfigMapsFromKAMs(client, ConfigMapProcessor.ConfigMapType.STATUS_MAPPING, configMapName);
                    map = cmProc.getConfigMap(client, kam, namespace, ConfigMapProcessor.ConfigMapType.STATUS_MAPPING, configMapName, builder);
 
                    // get config map for unregistered component status-mapping
                    if (map == null && builder.getConfigMap().entrySet().size() == 0) {
                        map = cmProc.getConfigMap(client, kam, ConfigMapProcessor.GLOBAL_NAMESPACE, ConfigMapProcessor.ConfigMapType.STATUS_MAPPING, ConfigMapProcessor.UNREGISTERED, builder);
                        // get configmap names declared in the KindActionMapping custom resources
                        configMapList = kam.getConfigMapsFromKAMs(client, ConfigMapProcessor.ConfigMapType.STATUS_MAPPING, ConfigMapProcessor.UNREGISTERED);
                    }
 
                    if (map != null) {
                        builder.merge(map);
                    } else {
                        if (Logger.isDebugEnabled()) 
                            Logger.log(CLASS_NAME, "getConfigMap", Logger.LogType.DEBUG, "configmap is null.");
                    }
                } else if (type.equals(SECTIONS_CONFIGMAP_TYPE)) {
                    // get configmap names declared in the KindActionMapping custom resources
                    configMapList = kam.getConfigMapsFromKAMs(client, ConfigMapProcessor.ConfigMapType.SECTION, configMapName);

                    SectionConfigMapProcessor scmProc = new SectionConfigMapProcessor(kind);
                    map = scmProc.getConfigMap(client, kam, namespace, configMapName, new SectionConfigMapBuilder());
                } else {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(getStatusMessageAsJSON("Invalid configMap type: " + type)).build();
                }

                if (Logger.isDebugEnabled()) 
                    Logger.log(CLASS_NAME, "getConfigMap", Logger.LogType.DEBUG, "configMapList " + configMapList.toString());
                
                JsonArray cmapJsonArray = toJsonArray(configMapList);
                final JsonObject cmapInfoJSON = new JsonObject();
                cmapInfoJSON.add("candidate-maps", cmapJsonArray);
                cmapInfoJSON.add("map", map);
                if (cmapInfoJSON != null) {
                    return Response.ok(cmapInfoJSON.toString()).build();
                } else {
                    // This should never happen.
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(getStatusMessageAsJSON("No object")).build();
                }
            }
        } catch (IOException | ApiException e) {
            if (Logger.isErrorEnabled()) {
                Logger.log(CLASS_NAME, "getConfigMaps", Logger.LogType.ERROR, "Caught Exception returning status: " + getResponseCode(e) + " " + e.toString());
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        } 
    }    

    private JsonArray toJsonArray(ArrayList<QName> configMapList) {
        JsonArray cmapArray = new JsonArray();
        for (int i=0; i<configMapList.size(); i++) {                                                             
            QName mapName = configMapList.get(i);  // list entry format: mapname@namespace
            String mapNameInString = mapName.getLocalPart() + "@" + mapName.getNamespaceURI();
            if (Logger.isDebugEnabled()) {
                Logger.log(CLASS_NAME, "toJsonArray", Logger.LogType.DEBUG, 
                    "configMapList["+i+"]="+mapNameInString);
            } 
            cmapArray.add(mapNameInString);
        } 
        return cmapArray;
    }

    private boolean isOwnerinfoNullOrEmpty(String apiVersion, String kind, String uid) {
        if ( ((apiVersion == null) || (apiVersion.isEmpty())) &&
             ((kind == null) || (kind.isEmpty())) &&
             ((uid == null) || (uid.isEmpty()))  )
            return true;
        else
            return false;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/resources/{resource-name}/{resource-kind}")
    @Operation(
            summary = "Computes the config maps of a Kubernetes resource running in a cluster.",
            description = "Returns the final computed configmap content for the given Kubernetes resource retrived from the cluster."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
                   @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
                   @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
                   @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getResourceConfigmap(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE)
        @PathParam("resource-name") @Parameter(description = "The name of the resource") String name,
        @PathParam("resource-kind") @Parameter(description = "The Kubernetes resource kind for the resource") String kind,
        @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("actions") 
                 @QueryParam("type") @Parameter(description = "The type of the configmap to retrieve and the values can be actions, status-mapping, or sections.") String type,
        @Pattern(regexp = API_VERSION_PATTERN_ZERO_OR_MORE) @DefaultValue("") @QueryParam("apiversion") @Parameter(description = "The apiVersion of the resource") String apiVersion,
        @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the resource") String namespace){
        
        try {  
            final ApiClient client = getApiClient();
            JsonObject map = null;

            JsonObject component = getResource(client, name, kind, apiVersion, namespace);

            if (type.equals(ACTIONS_CONFIGMAP_TYPE)) {
                ConfigMapProcessor cmProc = new ConfigMapProcessor(kind);
                map = cmProc.getConfigMap(client, component, ConfigMapProcessor.ConfigMapType.ACTION);
            } else if (type.equals(STATUS_MAPPING_CONFIGMAP_TYPE)) {
                ConfigMapProcessor cmProc = new ConfigMapProcessor(kind);
                map = cmProc.getConfigMap(client, component, ConfigMapProcessor.ConfigMapType.STATUS_MAPPING);
            } else if (type.equals(SECTIONS_CONFIGMAP_TYPE)) {
                SectionConfigMapProcessor scmProc = new SectionConfigMapProcessor(kind);
                map = scmProc.getConfigMap(client, component);
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(getStatusMessageAsJSON("Invalid configMap type: " + type)).build();
            }

            final JsonObject json = getItemAsObject(client, map);
            if (json != null) {
                return Response.ok(json.toString()).build();
            } else {
                // This should never happen.
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(getStatusMessageAsJSON("No object")).build();
            }

        } catch (IOException | ApiException e) {
            if (Logger.isErrorEnabled()) {
                Logger.log(CLASS_NAME, "getConfigMaps", Logger.LogType.ERROR, "Caught Exception returning status: " + getResponseCode(e) + " " + e.toString());
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    } 

    private JsonObject getResource(final ApiClient client, final String name, final String kind, String apiVersion,
                               final String namespace) throws ApiException {
        final String methodName = "getResource";
        if (Logger.isEntryEnabled()) {
            Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY,
                       "Name=" + name + ", kind=" + kind + ", apiVersion=" + apiVersion + ", namespace=" + namespace);
        }

        if (registry == null) {
            // Initialize the registry here if CDI failed to do it.
            registry = new ComponentInfoRegistry(client);
        }

        if (apiVersion == null || apiVersion.trim().length() == 0) {
            apiVersion = ComponentInfoRegistry.CORE_KIND_TO_API_VERSION_MAP.get(kind);
            if (apiVersion == null) {
                if (Logger.isErrorEnabled()) {
                    Logger.log(CLASS_NAME, "getResource", Logger.LogType.ERROR, "Api version is null.");
                }
                throw new ApiException(400, "getResource Unknown kind: " + kind);
            }
        }
        final Object o = registry.getNamespacedObject(client, kind, apiVersion, namespace, name);
        if (Logger.isExitEnabled()) {
            Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "");
        }
        return getItemAsObject(client, o);
    }
}
