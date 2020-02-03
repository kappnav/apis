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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.squareup.okhttp.Call;
import io.kubernetes.client.util.Watch.Response;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import com.ibm.kappnav.logging.Logger;

import application.rest.v1.KAppNavConfig;
import application.rest.v1.KAppNavEndpoint;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.JSON;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch;
import com.squareup.okhttp.OkHttpClient;
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
        Thread t = new Thread(new Runnable() {
            @SuppressWarnings("serial")
            @Override
            public void run() {
                while (true) {
                    try {                    
                        ApiClient client = KAppNavEndpoint.getApiClient();
                        OkHttpClient httpClient = client.getHttpClient();
                        // Infinite timeout
                        httpClient.setReadTimeout(0, TimeUnit.SECONDS);
                        client.setHttpClient(httpClient);
                        CustomObjectsApi coa = new CustomObjectsApi();
                        coa.setApiClient(client);
                        
                        // create a Watch to monitor Kappnav custom resource installed in kappnav namespace
                        Watch<Object> watch = null;
                        try {                            
                            watch = Watch.createWatch (
                                    client,
                                    coa.listNamespacedCustomObjectCall(KAPPNAV_CR_GROUP, KAPPNAV_CR_VERSION, KAPPNAV_NAMESPACE, KAPPNAV_CR_PLURAL, null, null, null, Boolean.TRUE, null, null),                              
                                    new TypeToken<Watch.Response<Object>>() {}.getType());
                                                        
                            // Note: While the watch is active this iterator loop will block waiting for notifications of custom resource changes from the Kube API.
                            for (Watch.Response<Object> item : watch) {                           
                                JsonObject o = KAppNavEndpoint.getItemAsObject(client, item.object);                               
                                if (o != null) {
                                    //only handle ADDED case
                                    switch (item.type) {
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
                        } catch (ApiException e) {                           
                            if (Logger.isDebugEnabled()) {
                                Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Caught Exception from running custom object watch: " + e.toString());
                            }
                        } finally {                           
                            // If the watch stops or fails delete the cache.
                            if (watch != null) {
                                watch.close();
                            }
                        }
                    } catch (Exception e) {
                        if (Logger.isDebugEnabled()) {
                            Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Caught Exception from watch initialization or shutdown: " + e.toString());
                        }
                    }                  
                }
            }
        });
        t.setName(WATCHER_THREAD_NAME);
        t.setDaemon(true);
        t.start();
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

 
   
        

    

