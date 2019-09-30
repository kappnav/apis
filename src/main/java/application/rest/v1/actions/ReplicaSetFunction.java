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

// replicaset() or replicaset(<deployment-namespace>,<deployment-name>)
// Returns the replica set name for the specified deployment.
// Return value structure is: { "ReplicaSet" : ["name1", "name2", ...] }
public class ReplicaSetFunction implements Function {
      
    private static final String DEPLOYMENT_KIND = "Deployment";
    private static final String REPLICA_SET_KIND = "ReplicaSet";
    private static final String REPLICA_SET_PROPERTY_NAME = "ReplicaSet";
    private static final String METADATA_PROPERTY_NAME = "metadata";

    @Override
    public String getName() {
        return "replicaset";
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
            // parameters.get(0) :: deployment namespace
            // parameters.get(1) :: deployment name
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
        
        // Retrieve the replicaSet for specified deployment.
        final Selector selector = Selector.getSelector(resource);
        if (!selector.isEmpty()) {
            final String labelSelector = selector.toString();
            try {
                Object o = registry.listClusterObject(client, REPLICA_SET_KIND, null, labelSelector, null, null);
                List<JsonObject> items = KAppNavEndpoint.getItemsAsList(client, o);                
                ReplicaSetResult result = new ReplicaSetResult();
                //loop over each replica set
                items.forEach(v -> {
                    JsonElement element = v.get(METADATA_PROPERTY_NAME);
                    if (element != null && element.isJsonObject()) {
                        JsonObject metadata = element.getAsJsonObject();   
                        //get replicaset name  
                        JsonElement rsName  = metadata.get("name");
                        if (rsName!= null && rsName.isJsonPrimitive()) {
                            result.add(rsName.getAsString());
                        }                                
                    }
                });                  
                return result.getJSON();
            }
            catch (ApiException e) {}
        }
        return null;
    }


    static final class ReplicaSetResult {
        private final JsonObject o;
        private final JsonArray rs;
        // Constructs:
        // {
        //   ReplicaSet: [ <name1>, <name2>, ... ]
        // } 
        public ReplicaSetResult() {
            o = new JsonObject();
            o.add(REPLICA_SET_PROPERTY_NAME, rs = new JsonArray());
        }
        public void add(final String name) {  
            //avoid duplicate name     
            if (! rs.toString().contains(name))
                rs.add(name);                
        }
        public String getJSON() {
            return o.toString();
        }
    }    
}
