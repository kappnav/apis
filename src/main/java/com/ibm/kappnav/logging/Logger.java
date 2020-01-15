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

/**
 * KAppNav logging.
 */
public class Logger { 

   // end user requests log level to select log types captured in log 
   public enum LogLevel { NONE, WARNING, ERROR, INFO, ENTRY, DEBUG, ALL }; 
   
   // code specifies log type it is writing 
   public enum LogType { ENTRY, EXIT, INFO, WARNING, ERROR, DEBUG }; 

   private static boolean[] enabled= new boolean[LogType.values().length];

   static {
        enabled[LogType.ERROR.ordinal()]= true;
        enabled[LogType.WARNING.ordinal()]= true;
        enabled[LogType.INFO.ordinal()]= true;

        // need to removed these 2 lines once issue 104 done
        // Uncomment these 3 lines to turn on debug when needed for now
        //enabled[LogType.ENTRY.ordinal()]= true;  
        //enabled[LogType.EXIT.ordinal()]= true;
        //enabled[LogType.DEBUG.ordinal()] = true;
    } // set default

    // return log message as string 
    static String getLogMessage(LogType logType, String logData) {
       return "["+logType+"] "+logData; 
    } 

    public static void log(String className, String methodName, LogType logType, String logData) {
      if ( enabled[logType.ordinal()] ) 
        System.out.println("[" +new SimpleDateFormat("MM/dd/yy HH:mm:ss:SSS z").format(new Date())  
            + "] " + Thread.currentThread().getId() + " " + className + " " + methodName 
            + " " + getLogMessage(logType, logData)); 
    }

   private static void setLogTypes(boolean value) { 
      for ( LogType type: LogType.values() ) { 
          enabled[type.ordinal()]= value; 
      } 
   } 

   public static void setLogLevel(String level) {
            level= level.toLowerCase(); 
            setLogTypes(false); 
            switch(level) { 
                case "none": 
                    break;               
                case "error":
                    enabled[LogType.ERROR.ordinal()]= true;  
                    break;
                case "warning": 
                    enabled[LogType.ERROR.ordinal()]= true;  
                    enabled[LogType.WARNING.ordinal()]= true;  
                    break;
                case "info": 
                    enabled[LogType.ERROR.ordinal()]= true;  
                    enabled[LogType.WARNING.ordinal()]= true;  
                    enabled[LogType.INFO.ordinal()]= true;  
                    break;
                case "entry": 
                    enabled[LogType.ERROR.ordinal()]= true;  
                    enabled[LogType.WARNING.ordinal()]= true;  
                    enabled[LogType.INFO.ordinal()]= true;  
                    enabled[LogType.ENTRY.ordinal()]= true;  
                    enabled[LogType.EXIT.ordinal()]= true;  
                    break;
                case "debug": 
                    setLogTypes(true); 
                    break;
                case "all": 
                    setLogTypes(true); 
                    break;
                default : // same as info
                    enabled[LogType.ERROR.ordinal()]= true;  
                    enabled[LogType.WARNING.ordinal()]= true;  
                    enabled[LogType.INFO.ordinal()]= true;  
                    Logger.log(Logger.class.getName(), "setLogLevel", Logger.LogType.DEBUG, "Log level was set to invalid value="+ level);
                    break;
            }           
   }  
}
