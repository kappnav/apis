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

import application.rest.v1.configmaps.ConfigMapProcessor;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;

@Path("/applications")
@Tag(ref="applications")
public class ApplicationsEndpoint extends KAppNavEndpoint {

    private static final String APPLICATIONS_PROPERTY_NAME = "applications";
    private static final String APPLICATION_PROPERTY_NAME = "application";
    private static final String ACTION_MAP_PROPERTY_NAME = "action-map";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Retrieve application objects and action maps.",
            description = "Returns a JSON structure of all application objects and their action maps, optionally filtered by namespace."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getApplications(@Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("") @QueryParam("namespace") @Parameter(description = "The namespace of the application") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final Object o;
            if (namespace.isEmpty()) {
                o = listApplicationObject(client);
            }
            else {
                o = listNamespacedApplicationObject(client, namespace);
            }
            return processApplications(client, getItemsAsList(client, o));
        }
        catch (IOException | ApiException e) {
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{application-name}")
    @Operation(
            summary = "Retrieve an application object and its action map.",
            description = "Returns a JSON structure containing the application object and action map for the specified application."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getApplicationAndMap(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("application-name") @Parameter(description = "The name of the application") String name, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the application") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final Object o = getNamespacedApplicationObject(client, namespace, name);
            return processApplications(client, getItemAsList(client, o));
        }
        catch (IOException | ApiException e) {
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }
    }

    private Response processApplications(ApiClient client, List<JsonObject> appObjects) throws ApiException {
        final AppResponse response = new AppResponse();
        final ConfigMapProcessor processor = new ConfigMapProcessor(APPLICATION_PROPERTY_NAME);
        appObjects.forEach(v -> {
            response.add(v, processor.getConfigMap(client, v, ConfigMapProcessor.ConfigMapType.ACTION));
        });
        return Response.ok(response.getJSON()).build();
    }

    static final class AppResponse {
        private final JsonObject o;
        private final JsonArray applications;
        // Constructs:
        // {
        //   applications: [ { application: {...}, action-map: {...} }, ... ]
        // }
        //
        // 'applications' is an array of objects.
        // Each object in the applications array is comprised of an application and action-map.
        // An application is an instance of the application CRD.
        // An action-map is a config map containing the action definitions belonging to the associated application. 
        public AppResponse() {
            o = new JsonObject();
            o.add(APPLICATIONS_PROPERTY_NAME, applications = new JsonArray());
        }
        public void add(final JsonObject application, final JsonObject actionMap) {
            final JsonObject tuple = new JsonObject();
            tuple.add(APPLICATION_PROPERTY_NAME, application != null ? application : new JsonObject());
            tuple.add(ACTION_MAP_PROPERTY_NAME, actionMap != null ? actionMap : new JsonObject());
            applications.add(tuple);
        }
        public String getJSON() {
            return o.toString();
        }
    }
}
