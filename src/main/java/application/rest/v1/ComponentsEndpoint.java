/*
 * Copyright 2019 IBM Corporation
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
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ibm.kappnav.logging.Logger;

import application.rest.v1.configmaps.ConfigMapProcessor;
import application.rest.v1.configmaps.SectionConfigMapProcessor;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;

@Path("/components")
@Tag(name = "components", description="kAppNav Components API")
public class ComponentsEndpoint extends KAppNavEndpoint {
    private static final String className = ComponentsEndpoint.class.getName();

    private static final String COMPONENTS_PROPERTY_NAME = "components";
    private static final String COMPONENT_PROPERTY_NAME = "component";
    private static final String ACTION_MAP_PROPERTY_NAME = "action-map";
    private static final String SECTION_MAP_PROPERTY_NAME = "section-map";
    private static final String KIND_PROPERTY_NAME = "kind";
    
    @Inject
    private ComponentInfoRegistry registry;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{application-name}")
    @Operation(
            summary = "Retrieve component objects and action and section maps for an application.",
            description = "Returns a JSON structure of all component objects and their action and section maps for the specified application."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getComponents(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("application-name") @Parameter(description = "The name of the application") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the application") String namespace) {
        try {
            final ApiClient client = getApiClient();
            if (registry == null) {
                // Initialize the registry here if CDI failed to do it.
                registry = new ComponentInfoRegistry(client);
            }
            final JsonObject o = ApplicationCache.getNamespacedApplicationObject(client, namespace, name);

            //get names from application and application annotations
            final List<String> namespaces = getNamespaceList(client, o, namespace);  
                                          
            final List<ComponentKind> componentKinds = ComponentKind.getComponentKinds(o, registry);
            final Selector selector = Selector.getSelector(o);
            return processComponentKinds(client, componentKinds, namespaces, selector, namespace, name);
        }
        catch (IOException | ApiException e) {
            if (Logger.isErrorEnabled()) {
                Logger.log(className, "getComponents", Logger.LogType.ERROR, "Caught Exception returning status: " + getResponseCode(e) + " " + e.toString());
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }   
    }
    
    private Response processComponentKinds(ApiClient client, List<ComponentKind> componentKinds, List<String> namespaces, Selector selector, String appNamespace, String appName) {
        final ComponentResponse response = new ComponentResponse();
        if (!selector.isEmpty()) {
            final String labelSelector = selector.toString();
            componentKinds.forEach(v -> {
                try {
                    Set<String> apiVersions = registry.getComponentGroupApiVersions(v);
                    for (String apiVersion : apiVersions) {
                        if (apiVersion != null) {
                            System.out.println("processComponentKinds using apiVersion: " + apiVersion + " for Application: " + appName +" componentKind group: " + v.group + " kind: " + v.kind);
                            if (!registry.isNamespaced(client, v.kind, apiVersion)) {
                                Object o = registry.listClusterObject(client, v.kind, apiVersion, null, labelSelector, null, null);
                                processComponents(client, response, v, getItemsAsList(client, o));
                            } else {
                                // If the component kind is namespaced, query components for each of the specified namespaces.
                                final String apiVersion1 = apiVersion;    // to avoid compiler error
                                namespaces.forEach(n -> {
                                    try {
                                        Object o = registry.listNamespacedObject(client, v.kind, apiVersion1, n, null, labelSelector, null, null);
                                        processComponents(client, response, v, getItemsAsList(client, o), appNamespace, appName);
                                    } catch (ApiException e) {
                                    }
                                });
                            }
                        } else {
                            if (Logger.isWarningEnabled()) {
                                Logger.log(className, "processComponentKinds", Logger.LogType.WARNING, " Application: " + appName +" componentKind group: " + v.group + " kind: " + v.kind + " not recognized. skipping");
                            }
                        }
                    }
                }
                catch (ApiException e) {
                    if (Logger.isErrorEnabled()) {
                        Logger.log(className, "processComponentKinds", Logger.LogType.DEBUG, "Caught ApiException " + e.toString());
                    }
                }
            });
        }
        return Response.ok(response.getJSON()).build();
    }
    
    private void processComponents(ApiClient client, ComponentResponse response, ComponentKind componentKind, List<JsonObject> components) {
        final ConfigMapProcessor processor = new ConfigMapProcessor(componentKind.kind);
        final SectionConfigMapProcessor sectionProcessor = new SectionConfigMapProcessor(componentKind.kind);
        components.forEach(v -> {
            // Add 'kind' property to components that are missing it.
            if (v.get(KIND_PROPERTY_NAME) == null) {
                v.addProperty(KIND_PROPERTY_NAME, componentKind.kind);
            }   
            response.add(v, processor.getConfigMap(client, v, ConfigMapProcessor.ConfigMapType.ACTION), sectionProcessor.processSectionMap(client, v));
        });
    }

    private void processComponents(ApiClient client, ComponentResponse response, ComponentKind componentKind, List<JsonObject> components, String appNamespace, String appName) {
        final ConfigMapProcessor processor = new ConfigMapProcessor(componentKind.kind);
        final SectionConfigMapProcessor sectionProcessor = new SectionConfigMapProcessor(componentKind.kind);
        components.forEach(v -> {
            // Add 'kind' property to components that are missing it.
            if (v.get(KIND_PROPERTY_NAME) == null) {
                v.addProperty(KIND_PROPERTY_NAME, componentKind.kind);
            }
                       
            // filter out recursive app from component list
            if (!(componentKind.kind.equals("Application") &&
                   getComponentName(v).equals(appName) &&
                   getComponentNamespace(v).equals(appNamespace))) {                                       
                response.add(v, processor.getConfigMap(client, v, ConfigMapProcessor.ConfigMapType.ACTION), sectionProcessor.processSectionMap(client, v));
            }
        });
    }    

    private List<String> getNamespaceList(ApiClient client, JsonObject o, String namespace) {
        final List<String> newNamespaces = new ArrayList<String>();
    
        //get namespaces from application
        List<String> appNamespaces = Collections.singletonList(namespace);

        //get kappnav.component.namespaces from application annotations
        List<String> kappnavNamespaces = getAnnotationNamespaces(o);

        kappnavNamespaces.addAll(appNamespaces);
        
        //remove duplicates
        for (String element : kappnavNamespaces) { 
            if (!newNamespaces.contains(element)) {
                newNamespaces.add(element); 
            } 
        }
        return newNamespaces;
    }
   
   
    static final class ComponentResponse {
        private final JsonObject o;
        private final JsonArray components;
        // Constructs:
        // {
        //   components: [ { component: {...}, action-map: {...}, section-map: {...} } ]
        // }
        public ComponentResponse() {
            o = new JsonObject();
            o.add(COMPONENTS_PROPERTY_NAME, components = new JsonArray());
        }
        public void add(final JsonObject component, final JsonObject actionMap, final JsonObject sectionMap) {
            final JsonObject tuple = new JsonObject();
            tuple.add(COMPONENT_PROPERTY_NAME, component != null ? component : new JsonObject());
            tuple.add(ACTION_MAP_PROPERTY_NAME, actionMap != null ? actionMap : new JsonObject());
            tuple.add(SECTION_MAP_PROPERTY_NAME, sectionMap != null ? sectionMap : new JsonObject()); 
            components.add(tuple);
        }
        public String getJSON() {
            return o.toString();
        }
    }
   
        
}
