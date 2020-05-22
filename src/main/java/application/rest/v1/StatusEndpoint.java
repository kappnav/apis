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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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

import com.google.gson.JsonObject;

import application.rest.v1.configmaps.ConfigMapProcessor;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;

import com.ibm.kappnav.logging.Logger;

@Path("/status")
@Tag(name = "status", description="kAppNav Status API")
public class StatusEndpoint extends KAppNavEndpoint {
    
    private static final String KIND_PROPERTY_NAME = "kind";
    
    @Inject
    private ComponentInfoRegistry registry;
    
    @Inject
    private KAppNavConfig config;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{resource-name}/{resource-kind}")
    @Operation(
            summary = "Computes the kAppNav status of a Kubernetes resource.",
            description = "Returns the computed kAppNav status object for the given Kubernetes resource."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response computeStatus(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("resource-name") @Parameter(description = "The name of the resource") String name,
            @PathParam("resource-kind") @Parameter(description = "The Kubernetes resource kind for the resource") String kind,
            @Pattern(regexp = API_VERSION_PATTERN_ZERO_OR_MORE) @DefaultValue("") @QueryParam("apiversion") @Parameter(description = "The apiVersion of the resource") String apiVersion,
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the resource") String namespace) {
        try {
            System.out.println("CPV: enter");
            final ApiClient client = getApiClient();
            System.out.println("CPV: got here -1a");
            final JsonObject resource = getResource(client, name, kind, urlDecode(apiVersion), namespace);
            System.out.println("CPV: got here -1b");
            // Add a 'kind' property to the resource if it is missing.
            if (resource.get(KIND_PROPERTY_NAME) == null) {
                System.out.println("CPV: got here - WTF?!");
                resource.addProperty(KIND_PROPERTY_NAME, kind);
            }
            System.out.println("CPV: got here 0a");
            final ConfigMapProcessor processor = new ConfigMapProcessor(kind);
            System.out.println("CPV: got here 0b");
            if (config == null) {
                // Initialize the config here if CDI failed to do it.
                config = new KAppNavConfig(client);
            }
            System.out.println("CPV: got here 1");
            final StatusProcessor statusProcessor = new StatusProcessor(config);
            System.out.println("CPV: got here 2");
            final JsonObject configMap = processor.getConfigMap(client, resource, ConfigMapProcessor.ConfigMapType.STATUS_MAPPING);
            System.out.println("CPV: got here 3");
            final JsonObject status = statusProcessor.getComponentStatus(client, registry, resource, configMap);
            System.out.println("CPV: got here 4");
            return Response.ok(status.toString()).build();
        }
        catch (IOException | ApiException e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(StatusEndpoint.class.getName(), "computeStatus", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + " " + e.toString());
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        } 
    }
    
    private JsonObject getResource(ApiClient client, String name, String kind, String apiVersion, String namespace) throws ApiException {
        if (registry == null) {
            // Initialize the registry here if CDI failed to do it.
            registry = new ComponentInfoRegistry(client);
        }
        System.out.println("CPV: getResource 1");
        final Object o = registry.getNamespacedObject(client, kind, apiVersion, namespace, name);
        System.out.println("CPV: getResource 2");
        return getItemAsObject(client, o);
    }

    // Decodes a URL encoded string using `UTF-8`
    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            if (Logger.isErrorEnabled()) {
                Logger.log(StatusEndpoint.class.getName(), "urlDecode", Logger.LogType.ERROR, "Caught UnsupportedEncodingException " + ex.toString());
            }
            throw new RuntimeException(ex.getCause());
        }
    }
}
