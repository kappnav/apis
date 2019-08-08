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

package application.rest.v1.actions;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import application.rest.v1.ComponentInfoRegistry;
import application.rest.v1.KAppNavEndpoint;
import application.rest.v1.Selector;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;

// podlist() or podlist(<deployment-namespace>,<deployment-name>)
// Returns a list of pod names for the specified deployment.
// Return value structure is: { "pods" : "[<name1>,<name2>,etc]" }
public class PodlistFunction implements Function {
    
    private static final String PODS_PROPERTY_NAME = "pods";
    
    private static final String DEPLOYMENT_KIND = "Deployment";
    private static final String POD_KIND = "Pod";

    @Override
    public String getName() {
        return "podlist";
    }

    @Override
    public boolean allowedParameterCount(int parameterCount) {
        return parameterCount == 0 || parameterCount == 2;
    }

    @Override
    public String invoke(ResolutionContext context, List<String> parameters) {
        
        final ApiClient client = context.getApiClient();
        final ComponentInfoRegistry registry = context.getComponentInfoRegistry();

        // Get the deployment object.
        final JsonObject resource;
        if (parameters.size() == 0) {
            if (!DEPLOYMENT_KIND.equals(context.getResourceKind())) {
                // The context resource isn't a deployment.
                return null;
            }
            resource = context.getResource();
        }
        // Two parameter function
        else {
            // parameters.get(0) :: namespace
            // parameters.get(1) :: name
            final String namespace = parameters.get(0);
            final String name = parameters.get(1);
            try {
                Object o = registry.getNamespacedObject(client, DEPLOYMENT_KIND, namespace, name);
                resource = KAppNavEndpoint.getItemAsObject(client, o);
            }
            catch (ApiException e) {
                return null;
            }  
        }
        
        // Retrieve the pods for the deployment.
        final Selector selector = Selector.getSelector(resource);
        if (!selector.isEmpty()) {
            final String labelSelector = selector.toString();
            try {
                Object o = registry.listClusterObject(client, POD_KIND, null, labelSelector, null, null);
                List<JsonObject> items = KAppNavEndpoint.getItemsAsList(client, o);
                PodlistResult result = new PodlistResult();
                items.forEach(v -> {
                    JsonElement element = v.get("metadata");
                    if (element != null && element.isJsonObject()) {
                        JsonObject metadata = element.getAsJsonObject();
                        element = metadata.get("name");
                        if (element != null && element.isJsonPrimitive()) {
                            result.add(element.getAsString());
                        }
                    }
                });
                return result.getJSON();
            }
            catch (ApiException e) {}
        }
        return null;
    }
    
    static final class PodlistResult {
        private final JsonObject o;
        private final JsonArray pods;
        // Constructs:
        // {
        //   pods: [ <name1>, <name2>, ... ]
        // } 
        public PodlistResult() {
            o = new JsonObject();
            o.add(PODS_PROPERTY_NAME, pods = new JsonArray());
        }
        public void add(final String name) {
            pods.add(name);
        }
        public String getJSON() {
            return o.toString();
        }
    }
}
