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
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1SecretList;

import com.ibm.kappnav.logging.Logger;

@Path("/secrets")
@Tag(name = "secrets", description="kAppNav Secrets API")
public class SecretsEndpoint extends KAppNavEndpoint {
    
    private static final String SECRETS_PROPERTY_NAME = "secrets";
    
    private static final String KIND_PROPERTY_NAME = "kind";
    private static final String SECRET_KIND = "Secret";
    
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{label-name}/{label-value}")
    @Operation(
            summary = "Retrieve a list of secrets that match the specified label selector.",
            description = "Returns a list of secret objects that match the specified label selector."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getSecrets(@PathParam("label-name") @Parameter(description = "The name of the label") String name,
            @PathParam("label-value") @Parameter(description = "The value of the label") String value) {
        try {
            final ApiClient client = getApiClient();
            final Selector s = new Selector().addMatchLabel(name, value); // <name>: <value>
            final Object o = listSecrets(client, s.toString());
            return processSecrets(client, getItemsAsList(client, o));  
        }
        catch (IOException | JsonParseException | ApiException e) {
            Logger.log(SecretsEndpoint.class.getName(), "getSecrets", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + " " + e.toString());
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }
    
    private Response processSecrets(ApiClient client, List<JsonObject> secretObjects) {
        final SecretsResponse response = new SecretsResponse();
        secretObjects.forEach(v -> {
            // Add 'kind' property to resources that are missing it.
            if (v.get(KIND_PROPERTY_NAME) == null) {
                v.addProperty(KIND_PROPERTY_NAME, SECRET_KIND);
            }
            response.add(v);
        });
        return Response.ok(response.getJSON()).build();
    }
    
    private Object listSecrets(ApiClient client, String selector) throws ApiException {
        final CoreV1Api api = new CoreV1Api();
        api.setApiClient(client);
        final V1SecretList secrets = api.listSecretForAllNamespaces(null, null, null, selector, null, null, null, null, null);
        return secrets;
    }
    
    static final class SecretsResponse {
        private final JsonObject o;
        private final JsonArray secrets;
        // Constructs:
        // {
        //   secrets: [ {...}, {...}, ... ]
        // }
        public SecretsResponse() {
            o = new JsonObject();
            o.add(SECRETS_PROPERTY_NAME, secrets = new JsonArray());
        }
        public void add(final JsonObject secret) {
            secrets.add(secret);
        }
        public String getJSON() {
            return o.toString();
        }
    }
}
