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

// apppodlist() or apppodlist(<application-namespace>,<application-name>)
// Returns a list of pod names from all deployments belonging to specified application.
// Return value structure is: { "pods" : "[<name1>,<name2>,etc]" }
public class AppPodlistFunction implements Function {
    
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
        String result = null;     

        if (parameters.size() == 0) {
            if (!APPLICATION_KIND.equals(context.getResourceKind())) {
                // The context resource isn't a deployment.
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
            try {
                //get application namespaced object
                Object o = registry.getNamespacedObject(client, APPLICATION_KIND, appNamespace, appName);
                resource = KAppNavEndpoint.getItemAsObject(client, o);      
                result = getPodListFromApp(client, resource, registry);         
            }
            catch (ApiException e) {
                return null;
            }  
        }
        return result;                   
    }


    private String getPodListFromApp(ApiClient client, JsonObject resource, ComponentInfoRegistry registry) {
        PodlistResult result = new PodlistResult();       
        final Selector aSelector = Selector.getSelector(resource);                
        if (!aSelector.isEmpty()) {
            final String alabelSelector = aSelector.toString();           
            try {  
                //retrieve deployment object from app with the labels matching 
                Object deplO = registry.listClusterObject(client, DEPLOYMENT_KIND, null, alabelSelector, null, null);
                List<JsonObject> deployments = KAppNavEndpoint.getItemsAsList(client, deplO); 
                deployments.forEach(d -> {
                    String deplName = null;
                    String deplNamespace = null;
                    JsonElement metadata = d.get(METADATA_PROPERTY_NAME);
                    //get deployment name and namespace
                    if (metadata != null && metadata.isJsonObject()) {
                        JsonObject metadataObj = metadata.getAsJsonObject();
                        JsonElement element  = metadataObj.get("namespace");                            
                        if (element != null && element.isJsonPrimitive()) {
                            deplNamespace = element.getAsString();
                        }
                        element  = metadataObj.get("name");
                        if (element != null && element.isJsonPrimitive()) {
                            deplName = element.getAsString();
                        }  
                    } 
                        
                    try {
                        //get deployment namespaced object
                        Object dO = registry.getNamespacedObject(client, DEPLOYMENT_KIND, deplNamespace, deplName);
                        Selector dSelector = Selector.getSelector(client, dO);  
                        if (!dSelector.isEmpty()) {        
                            final String dlabelSelector = dSelector.toString();
                            //retrieve pod object from deployment with the label matching
                            Object podO = registry.listClusterObject(client, POD_KIND, null, dlabelSelector, null, null);
                            List<JsonObject> items = KAppNavEndpoint.getItemsAsList(client, podO);
                            items.forEach(p -> {
                                JsonElement element = p.get(METADATA_PROPERTY_NAME);
                                if (element != null && element.isJsonObject()) {
                                    JsonObject pmetadata = element.getAsJsonObject();   
                                    //get pod name  
                                    JsonElement pName  = pmetadata.get("name");
                                    if (pName!= null && pName.isJsonPrimitive()) {
                                        result.add(pName.getAsString());
                                    }
                                }
                            });
                        }
                    } catch (ApiException e) {} 
                });
            } catch (ApiException e) {}           
        }
        return result.getJSON();
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
