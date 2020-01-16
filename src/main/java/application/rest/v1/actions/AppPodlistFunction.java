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
import application.rest.v1.ComponentKind;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;

import com.ibm.kappnav.logging.Logger;

// apppodlist() or apppodlist(<application-namespace>,<application-name>)
// Returns a list of pod names from all deployments belonging to specified application.
// Return value structure is: { "pods" : "[<name1>,<name2>,etc]" }
public class AppPodlistFunction implements Function {
    private static final String className = AppPodlistFunction.class.getName();
    
    private static final String PODS_PROPERTY_NAME = "pods";
    private static final String DEPLOYMENT_KIND = "Deployment";
    private static final String APPLICATION_KIND = "Application";
    private static final String POD_KIND = "Pod";
    private static final String METADATA_PROPERTY_NAME = "metadata";

    @Override
    public String getName() {
        return "apppodlist";
    }

    @Override
    public boolean allowedParameterCount(int parameterCount) {
        return parameterCount == 0 || parameterCount == 2;
    }

    @Override
    public String invoke(ResolutionContext context, List<String> parameters) {
        final ApiClient client = context.getApiClient();
        final ComponentInfoRegistry registry = context.getComponentInfoRegistry();
        final JsonObject resource; 
        
        if (parameters.size() == 0) {
            if (!APPLICATION_KIND.equals(context.getResourceKind())) {
                // The context resource isn't an application.
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "invoke", Logger.LogType.DEBUG, "The context resource isn't an application, returning null.");
                }
                return null;
            }
            resource = context.getResource();
        }
        // Two parameter function
        else {
            // parameters.get(0) :: appNamespace
            // parameters.get(1) :: appName
            final String appNamespace = parameters.get(0);
            final String appName = parameters.get(1);
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "invoke", Logger.LogType.DEBUG, "Two parameters function appNamespace=" + appNamespace + ", appName="+ appName);
            }

            try {
                //get application namespaced object
                Object o = registry.getNamespacedObject(client, APPLICATION_KIND, "", appNamespace, appName);
                resource = KAppNavEndpoint.getItemAsObject(client, o);                                   
            }
            catch (ApiException e) {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "invoke", Logger.LogType.DEBUG, "Caught ApiException, returning null.");
                }
                return null;
            }  
        }   
        List<ComponentKind> componentKinds = ComponentKind.getComponentKinds(client, resource);    
        return getPodListFromApp(client, resource, registry, componentKinds);                    
    }


    private String getPodListFromApp(ApiClient client, JsonObject resource, ComponentInfoRegistry registry, List<ComponentKind> componentKinds) {
        PodlistResult result = new PodlistResult();             
        //retrieve app namespace from the resource 
        String appNamespace = getNameSpaceFromResource(resource);

        //check if app contains "Deployment" component kind        
        componentKinds.forEach(v -> {  
            if (v.kind.equals("Deployment")) {
                final Selector aSelector = Selector.getSelector(resource);    
                if (!aSelector.isEmpty()) {
                    final String alabelSelector = aSelector.toString();           
                    try {  
                        //retrieve deployment object from app with the labels matching 
                        Object deplO = registry.listClusterObject(client, DEPLOYMENT_KIND, "", null, alabelSelector, null, null);
                        List<JsonObject> deployments = KAppNavEndpoint.getItemsAsList(client, deplO); 
                        deployments.forEach(d -> {                   
                            Selector dSelector = Selector.getSelector(d);  
                            if (!dSelector.isEmpty()) {        
                                final String dlabelSelector = dSelector.toString();                              
                                try {
                                    //retrieve pod object from deployment with the label matching
                                    Object podO = registry.listClusterObject(client, POD_KIND, "", null, dlabelSelector, null, null);
                                    List<JsonObject> items = KAppNavEndpoint.getItemsAsList(client, podO);
                                    items.forEach(p -> {
                                        JsonElement element = p.get(METADATA_PROPERTY_NAME);
                                        if (element != null && element.isJsonObject()) {
                                            JsonObject pmetadata = element.getAsJsonObject();                                                                                          
                                            //find pod's namespace matching with app's namespace 
                                            JsonElement pNamespace  = pmetadata.get("namespace");
                                            if (pNamespace!= null && pNamespace.isJsonPrimitive()) {                                           
                                                if (pNamespace.getAsString().equals(appNamespace)) {
                                                    //get pod name  
                                                    JsonElement pName  = pmetadata.get("name");
                                                    if (pName!= null && pName.isJsonPrimitive()) {
                                                        result.add(pName.getAsString());
                                                    }                                                   
                                                }
                                            } 
                                        }
                                    });
                                } catch (ApiException e) {
                                    if (Logger.isDebugEnabled()) {
                                        Logger.log(className, "getPodListFromApp", Logger.LogType.DEBUG, "Caught ApiException-1 " + e.toString());
                                    }
                                }
                            }
                        });
                    } catch (ApiException e) {
                        if (Logger.isDebugEnabled()) {
                            Logger.log(className, "getPodListFromApp", Logger.LogType.DEBUG, "Caught ApiException-2 " + e.toString());
                        }
                    }
                }
            } 
        });           
        return result.getJSON();
    }

    private String getNameSpaceFromResource(JsonObject resource) {
        String appnamespace = null;
        JsonElement element = resource.get(METADATA_PROPERTY_NAME);
        if (element != null && element.isJsonObject()) {
            JsonObject metadata = element.getAsJsonObject();                                                                                          
            JsonElement namespace  = metadata.get("namespace");
            if (namespace!= null && namespace.isJsonPrimitive()) {
                appnamespace = namespace.getAsString();
            }
        }
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "getNameSpaceFromResource", Logger.LogType.DEBUG, "appNamespace=" + appnamespace);
        }
        return appnamespace;
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
            if (! pods.toString().contains(name))
                pods.add(name);
        }
        public String getJSON() {
            return o.toString();
        }
    }
}

