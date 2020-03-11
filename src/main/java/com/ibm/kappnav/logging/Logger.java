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

package com.ibm.kappnav.logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import application.rest.v1.CustomResourceWatcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;

import application.rest.v1.KAppNavEndpoint;
import application.rest.v1.KAppNavConfig;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.JSON;
import io.kubernetes.client.apis.CustomObjectsApi;

import com.squareup.okhttp.OkHttpClient;


/**
 * KAppNav logging.
 */
public class Logger { 

    private static final String className = Logger.class.getName();
 
   // end user requests log level to select log types captured in log 
   public enum LogLevel { NONE, WARNING, ERROR, INFO, ENTRY, DEBUG, ALL }; 
   private static LogLevel currentLevel;
   
   // code specifies log type it is writing 
   public enum LogType { ENTRY, EXIT, INFO, WARNING, ERROR, DEBUG }; 

   private static boolean[] typeEnabled= new boolean[LogType.values().length];

   static { 
            //set initial logger level in case it is changed before kappnav is installed.  If no logging found in kappnav CR, set default level to INFO     
            setInitialLoggerLevel();     
            
            //start custom resource watcher to watch any kappnav CR is added/modified
            try {                
                CustomResourceWatcher.startCustomResourceWatcher();
            } catch (Exception e) {
                System.out.println("Caught Exception at CustomResourceWatcher : " + e.toString());               
            }
        } 

    // return log message as string 
    public static String getLogMessage(LogType logType, String logData) {
       return "["+logType+"] "+logData; 
    } 

    public static void log(String className, String methodName, LogType logType, String logData) {
      if ( typeEnabled[logType.ordinal()] ) 
        System.out.println("[" +new SimpleDateFormat("MM/dd/yy HH:mm:ss:SSS z").format(new Date())  
            + "] " + Thread.currentThread().getId() + " " + className + " " + methodName 
            + " " + getLogMessage(logType, logData)); 
    }

    // guard methods 
    public static boolean isEntryEnabled() { 
        return typeEnabled[LogType.ENTRY.ordinal()]; 
    } 
        
    public static boolean isExitEnabled() { 
        return typeEnabled[LogType.EXIT.ordinal()]; 
    } 
    
    public static boolean isInfoEnabled() { 
        return typeEnabled[LogType.INFO.ordinal()]; 
    } 
    
    public static boolean isWarningEnabled() { 
        return typeEnabled[LogType.WARNING.ordinal()]; 
    } 
    
    public static boolean isErrorEnabled() { 
        return typeEnabled[LogType.ERROR.ordinal()]; 
    } 
    
    public static boolean isDebugEnabled() { 
        return typeEnabled[LogType.DEBUG.ordinal()]; 
    }

    private static void setLogTypes(boolean value) { 
        for ( LogType type: LogType.values() ) { 
            typeEnabled[type.ordinal()]= value; 
        } 
    } 

    public static void setLogLevel(LogLevel level) {
        if (level == currentLevel) {
            return;
        }

        setLogTypes(false); 
        switch(level) { 
            case NONE: 
                break;               
            case ERROR:
                typeEnabled[LogType.ERROR.ordinal()]= true;  
                break;
            case WARNING: 
                typeEnabled[LogType.ERROR.ordinal()]= true;  
                typeEnabled[LogType.WARNING.ordinal()]= true;  
                break;
            case INFO: 
                typeEnabled[LogType.ERROR.ordinal()]= true;  
                typeEnabled[LogType.WARNING.ordinal()]= true;  
                typeEnabled[LogType.INFO.ordinal()]= true;  
                break;
            case DEBUG: 
                typeEnabled[LogType.ERROR.ordinal()]= true;  
                typeEnabled[LogType.WARNING.ordinal()]= true;  
                typeEnabled[LogType.INFO.ordinal()]= true;  
                typeEnabled[LogType.DEBUG.ordinal()] = true; 
                break;
            case ENTRY: 
                setLogTypes(true); 
                break;
            case ALL: 
                setLogTypes(true); 
                break;
        }

        if (isInfoEnabled())
            log(className, "setLogLevel", LogType.INFO, "Logging level is now " + level);
    } 
   
    // set initial logger level from operator CR before it is added 
    private static void setInitialLoggerLevel() {
        final String KAPPNAV_CR_GROUP = "kappnav.operator.kappnav.io";    
        final String KAPPNAV_CR_VERSION = "v1"; 
        final String KAPPNAV_CR_PLURAL = "kappnavs";
        final String KAPPNAV_NAME = "kappnav";
        final String KAPPNAV_NAMESPACE = KAppNavConfig.getkAppNavNamespace();
        final String KAPPNAV_CR_NAME = KAppNavConfig.getkAppNavCRName();

        // set default level to INFO 
        setLogLevel(Logger.LogLevel.INFO);

        try {
            ApiClient client = KAppNavEndpoint.getApiClient();
            CustomObjectsApi coa = new CustomObjectsApi();
            coa.setApiClient(client);      
            //invoke CustomObjectApi to get list of custom resources       
            Object cr = coa.listNamespacedCustomObject(KAPPNAV_CR_GROUP, KAPPNAV_CR_VERSION, KAPPNAV_NAMESPACE, KAPPNAV_CR_PLURAL, null, null, null, null);  
            JsonObject obj = KAppNavEndpoint.getItemAsObject(client, cr);
            
            if (obj != null) {              
                JsonElement items = obj.get("items");
                if (items != null && items.isJsonArray()) {
                    JsonArray itemsArray = items.getAsJsonArray();
                    itemsArray.forEach(o -> {
                        if (o != null && o.isJsonObject()) {
                            JsonObject itemO = o.getAsJsonObject();
                            String name = KAppNavEndpoint.getComponentName(itemO);
                            // Only check for kappnav CR
                            if (name.equals(KAPPNAV_CR_NAME)) {
                                JsonElement specE = itemO.get("spec");
                                if (specE != null && specE.isJsonObject()) {
                                    JsonObject specO = specE.getAsJsonObject();
                                    JsonElement loggingE = specO.get("logging"); 
                                    // if logging is specified in CR, set it to logger level       
                                    if (loggingE != null && loggingE.isJsonObject()) {
                                        JsonObject loggingObj = loggingE.getAsJsonObject();        
                                        if (loggingObj != null) {                                                                
                                            JsonElement levelE  = loggingObj.get("apis"); 
                                            if (levelE != null && levelE.isJsonPrimitive())  {               
                                                String level = levelE.getAsString();   
                                                // reset logger level                                                                                          
                                                if (level.equals("none"))
                                                    setLogLevel(Logger.LogLevel.NONE);
                                                if (level.equals("warning"))
                                                    setLogLevel(Logger.LogLevel.WARNING);
                                                if (level.equals("error"))
                                                    setLogLevel(Logger.LogLevel.ERROR);
                                                if (level.equals("entry"))
                                                    setLogLevel(Logger.LogLevel.ENTRY);
                                                if (level.equals("debug"))
                                                    setLogLevel(Logger.LogLevel.DEBUG);
                                                if (level.equals("all"))
                                                    setLogLevel(Logger.LogLevel.ALL);                                               
                                            }
                                        }
                                    } 
                                }
                            }
                        }                          
                    });
                }              
            }                               
        } catch (Exception e) {                       
            System.out.println("Caught Exception when setting initial logger level: " + e.toString());                      
        }   
        
    }
                       
}
