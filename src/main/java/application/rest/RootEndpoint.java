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

package application.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/")
@Tag(name = "resources", description="kAppNav Resources Map")
public class RootEndpoint {
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Retrieve a map of all resources in the kAppNav API.",
            description = "Returns a map of all resources in the kAppNav API."
            )
    public Response listResources(@Context UriInfo uriInfo) {
        String healthURL = (uriInfo.getAbsolutePath() + "/health").replaceAll("(?<!http:)\\/\\/", "/");
        String actionResolverURL = (uriInfo.getAbsolutePath() + "/resource").replaceAll("(?<!http:)\\/\\/", "/");
        String applicationURL = (uriInfo.getAbsolutePath() + "/application").replaceAll("(?<!http:)\\/\\/", "/");
        String applicationsURL = (uriInfo.getAbsolutePath() + "/applications").replaceAll("(?<!http:)\\/\\/", "/");
        String componentsURL = (uriInfo.getAbsolutePath() + "/components").replaceAll("(?<!http:)\\/\\/", "/");
        String configmapURL = (uriInfo.getAbsolutePath() + "/configmap").replaceAll("(?<!http:)\\/\\/", "/");
        String namespacesURL = (uriInfo.getAbsolutePath() + "/namespaces").replaceAll("(?<!http:)\\/\\/", "/");
        String secretURL = (uriInfo.getAbsolutePath() + "/secret").replaceAll("(?<!http:)\\/\\/", "/");
        String secretsURL = (uriInfo.getAbsolutePath() + "/secrets").replaceAll("(?<!http:)\\/\\/", "/");
        String statusURL = (uriInfo.getAbsolutePath() + "/status").replaceAll("(?<!http:)\\/\\/", "/");
        return Response.ok("{\"actions\":\"" + actionResolverURL +
                "\",\"application\":\"" + applicationURL +
                "\",\"applications\":\"" + applicationsURL +
                "\",\"components\":\"" + componentsURL +
                "\",\"configmap\":\"" + configmapURL +
                "\",\"namespaces\":\"" + namespacesURL +
                "\",\"secret\":\"" + secretURL +
                "\",\"secrets\":\"" + secretsURL +
                "\",\"status\":\"" + statusURL +
                "\",\"health\":\"" + healthURL + "\"}").build();
    }
}
