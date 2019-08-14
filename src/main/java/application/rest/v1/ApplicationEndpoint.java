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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;

@Path("/application")
@Tag(name = "application", description="kAppNav Application CRUD API")
public class ApplicationEndpoint extends KAppNavEndpoint {
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{application-name}")
    @Operation(
            summary = "Retrieve an application.",
            description = "Returns the JSON application object for the specified application."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getApplication(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("application-name") @Parameter(description = "The name of the application") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the application") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final Object o = getNamespacedApplicationObject(client, namespace, name);
            JsonElement element = client.getJSON().getGson().toJsonTree(o);
            JsonObject json = element.getAsJsonObject();
            return Response.ok(json.toString()).build();
        }
        catch (IOException | ApiException e) {
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }

    @POST   
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Create an application.",
            description = "Creates a new application object in the specified namespace from the input JSON object."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response createApplication(String jsonstr, @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the application") String namespace) {
        try {

            final ApiClient client = getApiClient();
            JsonParser parser= new JsonParser(); 
            JsonElement element= parser.parse(jsonstr);
            JsonObject json= element.getAsJsonObject();
            createNamespacedApplicationObject(client, namespace, json);

            return Response.ok(getStatusMessageAsJSON("OK")).build();
        }
        catch (IOException | JsonSyntaxException | ApiException e) {
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }

    @PUT  
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{application-name}")
    @Operation(
            summary = "Update an application.",
            description = "Update the specified application object in the specified namespace from the input JSON object."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response replaceApplication(String jsonstr, @Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("application-name") @Parameter(description = "The name of the application") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the application") String namespace) {
        try {

            final ApiClient client = getApiClient();
            JsonParser parser= new JsonParser(); 
            JsonElement element= parser.parse(jsonstr);
            JsonObject json= element.getAsJsonObject();
            replaceNamespacedApplicationObject(client, namespace, name, json);

            return Response.ok(getStatusMessageAsJSON("OK")).build();
        }
        catch (IOException | JsonSyntaxException | ApiException e) {
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }

    @DELETE  
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{application-name}")
    @Operation(
            summary = "Delete an application.",
            description = "Delete the specified application object."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response deleteApplication(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("application-name") @Parameter(description = "The name of the application") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the application") String namespace) {
        try {
            final ApiClient client = getApiClient();
            deleteNamespacedApplicationObject(client, namespace, name);
            return Response.ok(getStatusMessageAsJSON("OK")).build();
        }
        catch (IOException | ApiException e) {
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }

}
