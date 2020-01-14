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

import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
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
import com.google.gson.JsonParseException;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1DeleteOptions;

import com.ibm.kappnav.logging.Logger;

@Path("/configmap")
@Tag(name = "configmap", description="kAppNav ConfigMap CRUD API")
public class ConfigMapEndpoint extends KAppNavEndpoint {
    private static final String className = ConfigMapEndpoint.class.getName();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{configmap-name}")
    @Operation(
            summary = "Retrieve a config map.",
            description = "Returns the JSON application object for the specified config map."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getConfigMap(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("configmap-name") @Parameter(description = "The name of the config map") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the config map") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);
            final V1ConfigMap map = api.readNamespacedConfigMap(encodeURLParameter(name), encodeURLParameter(namespace), null, null, null);
            final JsonObject json = getItemAsObject(client, map);
            if (json != null) {
                return Response.ok(json.toString()).build();
            }
            else {
                // This should never happen.
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(getStatusMessageAsJSON("No object")).build();
            }   
        }
        catch (IOException | ApiException e) {
            Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + e.toString());
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }
    
    @POST   
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Create a config map.",
            description = "Creates a new config map object in the specified namespace from the input JSON object."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response createConfigMap(String jsonstr, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the config map") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);
            final V1ConfigMap map = client.getJSON().deserialize(jsonstr, V1ConfigMap.class);
            api.createNamespacedConfigMap(encodeURLParameter(namespace), map, null);
            return Response.ok(getStatusMessageAsJSON("OK")).build();
        }
        catch (IOException | JsonParseException | ApiException e) {
            Logger.log(className, "createConfigMap", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + e.toString());
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }
    
    @PUT  
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{configmap-name}")
    @Operation(
            summary = "Update a config map.",
            description = "Update the specified config map object in the specified namespace from the input JSON object."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response replaceConfigMap(String jsonstr, @Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("configmap-name") @Parameter(description = "The name of the config map") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the config map") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);
            final V1ConfigMap map = client.getJSON().deserialize(jsonstr, V1ConfigMap.class);
            api.replaceNamespacedConfigMap(encodeURLParameter(name), encodeURLParameter(namespace), map, null);
            return Response.ok(getStatusMessageAsJSON("OK")).build();
        }
        catch (IOException | JsonParseException | ApiException e) {
            Logger.log(className, "replaceConfigMap", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + e.toString());
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }
    
    @DELETE  
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{configmap-name}")
    @Operation(
            summary = "Delete a config map.",
            description = "Delete the specified config map object."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response deleteConfigMap(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("configmap-name") @Parameter(description = "The name of the config map") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the config map") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);
            final V1DeleteOptions options = new V1DeleteOptions();
            api.deleteNamespacedConfigMap(encodeURLParameter(name), encodeURLParameter(namespace), options, null, 0, true, "");
            return Response.ok(getStatusMessageAsJSON("OK")).build();
        }
        catch (IOException | ApiException e) {
            Logger.log(className, "deleteConfigMap", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + e.toString());
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }
}
