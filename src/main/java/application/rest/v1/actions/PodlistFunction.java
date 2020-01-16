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

import com.ibm.kappnav.logging.Logger;

// podlist() or podlist(<deployment-namespace>,<deployment-name>)
// Returns a list of pod names for the specified deployment.
// Return value structure is: { "pods" : "[<name1>,<name2>,etc]" }
public class PodlistFunction implements Function {
    private static final String className = PodlistFunction.class.getName();

    private static final String PODS_PROPERTY_NAME = "pods";
    
    private static final String DEPLOYMENT_KIND = "Deployment";
    private static final String POD_KIND = "Pod";
    private static final String POD_APIVERSION = "/v1"; // means group="", version=v1
    private static final String METADATA_PROPERTY_NAME = "metadata";

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
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "invoke", Logger.LogType.DEBUG, "The context resource isn't a deployment but " + context.getResourceKind() + ". Returning null.");
                }
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
                Object o = registry.getNamespacedObject(client, DEPLOYMENT_KIND, namespace, name, "");
                resource = KAppNavEndpoint.getItemAsObject(client, o);
            }
            catch (ApiException e) {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "invoke", Logger.LogType.DEBUG, "Returning null because it caught ApiException " + e.toString());
                }
                return null;
            }  
        }
        return getPodListFromDeployment(client, resource, registry);  
    }
     
    private String getPodListFromDeployment(ApiClient client, JsonObject resource, ComponentInfoRegistry registry) {
        PodlistResult result = new PodlistResult();   
        //retrieve deployment namespace from the resource
        String deplNamespace = getNameSpaceFromResource(resource);

        // Retrieve the pods for the deployment.
        final Selector selector = Selector.getSelector(resource);
        if (!selector.isEmpty()) {
            final String labelSelector = selector.toString();
            try {
                Object o = registry.listClusterObject(client, POD_KIND, POD_APIVERSION, null, labelSelector, null, null);
                List<JsonObject> items = KAppNavEndpoint.getItemsAsList(client, o);
                
                items.forEach(v -> {
                    JsonElement element = v.get(METADATA_PROPERTY_NAME);
                    if (element != null && element.isJsonObject()) {
                        JsonObject metadata = element.getAsJsonObject();
                        //find pod's namespace matching with deployment's namespace 
                        JsonElement pNamespace  = metadata.get("namespace");
                        if (pNamespace!= null && pNamespace.isJsonPrimitive()) {                                           
                            if (pNamespace.getAsString().equals(deplNamespace)) {
                                //get pod name  
                                JsonElement pName  = metadata.get("name");
                                if (pName!= null && pName.isJsonPrimitive()) {
                                    result.add(pName.getAsString());
                                }
                            }    
                        }                           
                    }
                });               
            }
            catch (ApiException e) {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "getPodListFromDeployment", Logger.LogType.DEBUG, "Caught ApiException " + e.toString());
                }
            }
        }
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "getPodListFromDeployment", Logger.LogType.DEBUG, "Result=" + result.getJSON());
        }
        return result.getJSON();
    }

    private String getNameSpaceFromResource(JsonObject resource) {
        if (Logger.isEntryEnabled()) {
            Logger.log(className, "getNameSpaceFromResource", Logger.LogType.ENTRY, "For resource="+ resource.toString());
        }
        
        String deplnamespace = null;
        JsonElement element = resource.get(METADATA_PROPERTY_NAME);
        if (element != null && element.isJsonObject()) {
            JsonObject metadata = element.getAsJsonObject();                                                                                          
            JsonElement namespace  = metadata.get("namespace");
            if (namespace!= null && namespace.isJsonPrimitive()) {
                deplnamespace = namespace.getAsString();
            }
        }
        if (Logger.isExitEnabled()) {
            Logger.log(className, "getNameSpaceFromResource", Logger.LogType.EXIT, "Result=" + deplnamespace);
        }
        return deplnamespace;
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
