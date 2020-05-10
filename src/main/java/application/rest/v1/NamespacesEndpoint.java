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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1NamespaceList;

import com.ibm.kappnav.logging.Logger;

@Path("/namespaces")
@Tag(name = "namespaces", description="Kubernetes Namespace List")
public class NamespacesEndpoint extends KAppNavEndpoint {
    
    private static final String NAMESPACES_PROPERTY_NAME = "namespaces";
    
    // For junit test only
    private CoreV1Api cv1a = null;
    void setCoreV1ApiForInternal(CoreV1Api cv1a) {
       this.cv1a = cv1a;
    }

    private CoreV1Api getCoreV1ApiForInternal() {
        if (cv1a == null) {
            final CoreV1Api api = getCoreV1ApiForInternal();
            return api;
        } else {
            return cv1a;
        }
    }
   
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Retrieve the list of namespaces from Kubernetes.",
            description = "Returns a list of namespace objects for each of the namespaces in the Kubernetes cluster."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getNamespaceList() {
        try {
            final ApiClient client = getApiClient();
            final Object o = listNamespaces(client);
            return processNamespaces(client, getItemsAsList(client, o));
        }
        catch (IOException | ApiException e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(NamespacesEndpoint.class.getName(), "getNamespaceList", Logger.LogType.DEBUG, "Caught Exception returning status: " + getResponseCode(e) + e.toString());
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }
    
    private Response processNamespaces(ApiClient client, List<JsonObject> appObjects) {
        final NamespacesResponse response = new NamespacesResponse();
        appObjects.forEach(v -> {
            response.add(v);
        });
        return Response.ok(response.getJSON()).build();
    }
    
    private Object listNamespaces(ApiClient client) throws ApiException {
        final CoreV1Api api = getCoreV1ApiForInternal();
        api.setApiClient(client);
        final V1NamespaceList namespaces = api.listNamespace(null, null, null, null, null, null, null, null, null);
        return namespaces;
    }
    
    static final class NamespacesResponse {
        private final JsonObject o;
        private final JsonArray namespaces;
        // Constructs:
        // {
        //   namespaces: [ {...}, {...}, ... ]
        // }
        public NamespacesResponse() {
            o = new JsonObject();
            o.add(NAMESPACES_PROPERTY_NAME, namespaces = new JsonArray());
        }
        public void add(final JsonObject namespace) {
            namespaces.add(namespace);
        }
        public String getJSON() {
            return o.toString();
        }
    }
}
