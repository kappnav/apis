/*
 * Copyright 2020 IBM Corporation
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.kappnav.logging.Logger;
import com.squareup.okhttp.Call;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.util.Watch.Response;

/**
 * Watch kappnav custom resource (CR) that have been installed by the kAppNav operator. 
 */
public class CustomResourceWatcher {

    private static final String CLASS_NAME = CustomResourceWatcher.class.getName();
    private static final String KAPPNAV_NAMESPACE = KAppNavConfig.getkAppNavNamespace();
    private static final String KAPPNAV_CR_NAME = KAppNavConfig.getkAppNavCRName();
    private static final String KAPPNAV_CR_GROUP = "kappnav.operator.kappnav.io";    
    private static final String KAPPNAV_CR_VERSION = "v1"; 
    private static final String KAPPNAV_CR_PLURAL = "kappnavs";
     
    // Name of the watcher thread.
    private static final String WATCHER_THREAD_NAME = "kAppNav Custom Resource Watcher";

    public static void startCustomResourceWatcher() {
        Watcher.start(new Watcher.Handler<Object>() {

            @Override
            public String getWatcherThreadName() {
                return WATCHER_THREAD_NAME;
            }

            @Override
            public Call createWatchCall(ApiClient client) throws ApiException {
                CustomObjectsApi coa = new CustomObjectsApi();
                coa.setApiClient(client);
                return coa.listNamespacedCustomObjectCall(KAPPNAV_CR_GROUP, KAPPNAV_CR_VERSION, KAPPNAV_NAMESPACE, KAPPNAV_CR_PLURAL, null, null, null, Boolean.TRUE, null, null);
            }

            @Override
            public void processResponse(ApiClient client, Response<Object> response) {
                JsonObject o = KAppNavEndpoint.getItemAsObject(client, response.object);                               
                if (o != null) {
                    // Only handle ADDED and MODIFIED cases
                    switch (response.type) {
                        case "ADDED":
                            // check if Logging is specified in operator's CR, invoke Logger.setLogLevel() if it is specified
                            //System.out.println("kappnav CR is added");
                            setLoggerLevel(o);
                            break;
                        case "MODIFIED":
                            // check if Logging is specified in operator's CR, invoke Logger.setLogLevel() if it is specified
                            //System.out.println("kappnav CR is modified");
                            setLoggerLevel(o);
                            break;
                        case "DELETED":
                            //resource deleted - ignoring
                            break;                                        
                    }                           
                }
            }

            @Override
            public void shutdown(ApiClient client) {}
            
        }, true);
    }

    // set logger level
    private static void setLoggerLevel(JsonObject o) { 
        String name = KAppNavEndpoint.getComponentName(o);
        // Only check for kappnav CR
        if (name.equals(KAPPNAV_CR_NAME)) {
            JsonObject specObj = o.getAsJsonObject("spec");
            if (specObj != null) {
                JsonElement loggingE = specObj.get("logging");
                if (loggingE != null && loggingE.isJsonObject()) {
                    JsonObject loggingObj = loggingE.getAsJsonObject();        
                    if (loggingObj != null) { 
                        // only check for apis setting for now                                                               
                        JsonElement levelE  = loggingObj.get("apis"); 
                        if (levelE != null && levelE.isJsonPrimitive())  {               
                            String level = levelE.getAsString();
                            
                            // if log level not set, set to default "info"
                            if (level == null || level.equals("info")) 
                                Logger.setLogLevel(Logger.LogLevel.INFO);
                            if (level.equals("none"))
                                Logger.setLogLevel(Logger.LogLevel.NONE);
                            if (level.equals("warning"))
                                Logger.setLogLevel(Logger.LogLevel.WARNING);
                            if (level.equals("error"))
                                Logger.setLogLevel(Logger.LogLevel.ERROR);
                            if (level.equals("entry"))
                                Logger.setLogLevel(Logger.LogLevel.ENTRY);
                            if (level.equals("debug"))
                                Logger.setLogLevel(Logger.LogLevel.DEBUG);
                            if (level.equals("all"))
                                Logger.setLogLevel(Logger.LogLevel.ALL);    
                        }
                    }
                } 
            }
        }                                                    
    }
 }