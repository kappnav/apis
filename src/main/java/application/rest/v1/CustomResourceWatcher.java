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

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.kappnav.logging.Logger;
import com.squareup.okhttp.Call;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch;

/**
 * Watch kappnav custom resource (CR) that have been installed by the kAppNav operator. 
 */
public class CustomResourceWatcher {

    private static final String CLASS_NAME = CustomResourceWatcher.class.getName();
    private static final String KAPPNAV_NAMESPACE = KAppNavConfig.getkAppNavNamespace();
    private static final String KAPPNAV_CR_NAME = KAppNavConfig.getkAppNavCRName();
    private static final String UI_LOG_LEVEL_API = KAppNavConfig.getUILogLevelAPI();
    private static final String KAPPNAV_CR_GROUP = "kappnav.operator.kappnav.io";    
    private static final String KAPPNAV_CR_VERSION = "v1"; 
    private static final String KAPPNAV_CR_PLURAL = "kappnavs";
     
    // Name of the watcher thread.
    private static final String WATCHER_THREAD_NAME = "kAppNav Custom Resource Watcher";
    
    //Needed so that we are not making REST calls to update the UI logger level everytime the kappnav CR is updated (which is very frequent)
    private static String CURRENT_UI_LOG_LEVEL = "info";

    public static void startCustomResourceWatcher() {
        Watcher.start(new Watcher.Handler<Object>() {

            @Override
            public String getWatcherThreadName() {
                return WATCHER_THREAD_NAME;
            }
            
            @Override
            public List<Object> listResources(ApiClient client, AtomicReference<String> resourceVersion)
                    throws ApiException {
                CustomObjectsApi coa = new CustomObjectsApi();
                coa.setApiClient(client);
                Object o = coa.listNamespacedCustomObject(KAPPNAV_CR_GROUP, KAPPNAV_CR_VERSION, KAPPNAV_NAMESPACE, KAPPNAV_CR_PLURAL, null, null, null, null);
                return Watcher.processCustomObjectsApiList(client, o, resourceVersion);
            }

            @Override
            public Call createWatchCall(ApiClient client, String resourceVersion) throws ApiException {
                CustomObjectsApi coa = new CustomObjectsApi();
                coa.setApiClient(client);
                return coa.listNamespacedCustomObjectCall(KAPPNAV_CR_GROUP, KAPPNAV_CR_VERSION, KAPPNAV_NAMESPACE, KAPPNAV_CR_PLURAL, null, null, resourceVersion, Boolean.TRUE, null, null);
            }
            
            @SuppressWarnings("serial")
            @Override
            public Type getWatchType() {
                return new TypeToken<Watch.Response<Object>>() {}.getType();
            }

            @Override
            public void processResponse(ApiClient client, String type, Object object) {
                JsonObject o = KAppNavEndpoint.getItemAsObject(client, object);                               
                if (o != null) {
                    // Only handle ADDED and MODIFIED cases
                    switch (type) {
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
            public void reset(ApiClient client) {}
            
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
                        // check for apis setting                                                        
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
                        
                        // check for ui log setting
                        if (UI_LOG_LEVEL_API != null) {
                            JsonElement levelUi = loggingObj.get("ui");
                            if (levelUi != null && levelUi.isJsonPrimitive()) {
                                // match levels with levels that the UI supports
                                String newLevel = levelUi.getAsString();
                                if (newLevel.equals("warning")) {
                                    newLevel = "warn";
                                } else if (newLevel.equals("none")) {
                                    newLevel = "off";
                                } else if (newLevel.equals("entry")) {
                                    newLevel = "trace";
                                }

                                if (!CURRENT_UI_LOG_LEVEL.equals(newLevel)) {
                                    setUILogLevel(newLevel);
                                }
                            }
                        }
                    }
                } 
            }
        }                                                    
    }

    public static void setUILogLevel(String level) {
        String methodName = "setUILogLevel";
        if (Logger.isEntryEnabled()) {
            Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY, level);
        }

        String requestString = "{\"level\": \"" + level + "\"}";
        UriBuilder uriBuilder = UriBuilder.fromPath(UI_LOG_LEVEL_API);
        Client client = ClientBuilder.newClient();
        Response response = client.target(uriBuilder).request(MediaType.APPLICATION_JSON)
                .post(Entity.json(requestString));
        int status = response.getStatus();
        client.close();
        
        if(status == 200)
            CURRENT_UI_LOG_LEVEL = level;
        
        if (status != 200 && Logger.isErrorEnabled()) {
            Logger.log(CLASS_NAME, methodName, Logger.LogType.ERROR,
                    "Failed to set UI log level to " + level + ". Response status was: " + status);
        }
        if (Logger.isExitEnabled()) {
            Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "Response status: " + status);
        }
    }

}