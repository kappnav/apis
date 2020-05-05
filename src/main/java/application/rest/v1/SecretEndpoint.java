/*
 * Copyright 2019, 2020 IBM Corporation
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
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1Secret;

import com.ibm.kappnav.logging.Logger;

@Path("/secret")
@Tag(name = "secret", description="kAppNav Secret CRUD API")
public class SecretEndpoint extends KAppNavEndpoint {
    private static final String className = SecretEndpoint.class.getName();

    // For junit test only
    private CoreV1Api cv1a = null;
	void setCoreV1ApiForInternal(CoreV1Api cv1a) {
		this.cv1a = cv1a;
	}

	private CoreV1Api getCoreV1ApiForInternal() throws IOException {
		if (cv1a == null) {
			final CoreV1Api api = new CoreV1Api();
            return api;
		} else {
			return cv1a;
		}
	}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{secret-name}")
    @Operation(
            summary = "Retrieve a secret.",
            description = "Returns the JSON application object for the specified secret."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (JSON input is malformed)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getSecret(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("secret-name") @Parameter(description = "The name of the secret") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the secret") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final CoreV1Api api = getCoreV1ApiForInternal();
            api.setApiClient(client);
            final V1Secret secret = api.readNamespacedSecret(encodeURLParameter(name), encodeURLParameter(namespace), null, null, null);
            final JsonObject json = getItemAsObject(client, secret);
            if (json != null) {
                return Response.ok(json.toString()).build();
            }
            else {
                // This should never happen.
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "resolve", Logger.LogType.ERROR, "Should never happen. No object");
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(getStatusMessageAsJSON("No object")).build();
            }   
        }
        catch (IOException | ApiException e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getSecret", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + " " + e.toString());
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }
    
    @POST   
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Create a secret.",
            description = "Creates a new secret object in the specified namespace from the input JSON object."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (JSON input is malformed)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response createSecret(String jsonstr, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the secret") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final CoreV1Api api = getCoreV1ApiForInternal();
            api.setApiClient(client);
            final V1Secret secret = client.getJSON().deserialize(jsonstr, V1Secret.class);
            api.createNamespacedSecret(encodeURLParameter(namespace), secret, null);
            return Response.ok(getStatusMessageAsJSON("OK")).build();
        }
        catch (IOException | JsonParseException | ApiException e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "createSecret", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + " " + e.toString());
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }
    
    @PUT  
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{secret-name}")
    @Operation(
            summary = "Update a secret.",
            description = "Update the specified secret object in the specified namespace from the input JSON object."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (JSON input is malformed)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response replaceSecret(String jsonstr, @Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("secret-name") @Parameter(description = "The name of the secret") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the secret") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final CoreV1Api api = getCoreV1ApiForInternal();
            api.setApiClient(client);
            final V1Secret secret = client.getJSON().deserialize(jsonstr, V1Secret.class);
            api.replaceNamespacedSecret(encodeURLParameter(name), encodeURLParameter(namespace), secret, null);
            return Response.ok(getStatusMessageAsJSON("OK")).build();
        }
        catch (IOException | JsonParseException | ApiException e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "replaceSecret", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + " " + e.toString());
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }
    
    @DELETE  
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{secret-name}")
    @Operation(
            summary = "Delete a secret.",
            description = "Delete the specified secret object."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (JSON input is malformed)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response deleteSecret(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("secret-name") @Parameter(description = "The name of the secret") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the secret") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final CoreV1Api api = getCoreV1ApiForInternal();
            api.setApiClient(client);
            final V1DeleteOptions options = new V1DeleteOptions();
            api.deleteNamespacedSecret(encodeURLParameter(name), encodeURLParameter(namespace), options, null, 0, true, "");
            return Response.ok(getStatusMessageAsJSON("OK")).build();
        }
        catch (IOException | ApiException e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "deleteSecret", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + " " + e.toString());
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }
}
