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

   private static boolean[] typeEnabled= new boolean[LogType.values().length];

   static { setLogLevel(LogLevel.INFO); } // set default 

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
                    typeEnabled[LogType.ENTRY.ordinal()]= true;  
                    typeEnabled[LogType.EXIT.ordinal()]= true;  
                    break;
                case ENTRY: 
                    setLogTypes(true); 
                    break;
                case ALL: 
                    setLogTypes(true); 
                    break;
                default : // same as info
                    typeEnabled[LogType.ERROR.ordinal()]= true;  
                    typeEnabled[LogType.WARNING.ordinal()]= true;  
                    typeEnabled[LogType.INFO.ordinal()]= true;  
                    Logger.log(Logger.class.getName(), "setLogLevel", Logger.LogType.DEBUG, "Log level was set to invalid value="+ level);
                    break;
            }           
   }  
}
