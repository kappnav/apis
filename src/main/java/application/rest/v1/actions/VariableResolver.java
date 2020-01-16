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

import application.rest.v1.actions.ResolutionContext.ResolvedValue;
import com.ibm.kappnav.logging.Logger;

// ${var.<variable-name>}
// e.g. ${var.nodePort}
public class VariableResolver implements Resolver {

    @Override
    public String getName() {
        return "var";
    }

    // parameter 'suffix' is either variable-name or variable-name, default.string-constant 
    @Override
    public String resolve(ResolutionContext context, String suffix) throws PatternException {
        // Check if default.string-constant specified. If so, separate variable name from default value.
        String defaultValue= null; 
        int pos= suffix.indexOf(','); 
        if ( pos >= 0 ) { 
            defaultValue= suffix.substring(pos+1,suffix.length()); // grab default value expression 
            defaultValue= defaultValue.substring(defaultValue.indexOf('.')+1,
                          defaultValue.length()); // strip off 'default.' prefix
            suffix= suffix.substring(0,pos); // grab variable name 
        }

        if (Logger.isDebugEnabled()) {
            Logger.log(VariableResolver.class.getName(), "resolve", Logger.LogType.DEBUG, "For suffix="+suffix);
        }

        // Immediately return the value if the variable has been previously resolved.
        String value = context.getResolvedVariable(suffix);
        if (value != null) {
            if (Logger.isDebugEnabled()) {
                Logger.log(VariableResolver.class.getName(), "resolve", Logger.LogType.DEBUG, "Result=" + value);
            }
            return value;
        } 

        // Guard against cycles in variable definitions.
        // (e.g. var x = "${var.y}", var y = "${var.z}", var z = "${var.x}").
        if (context.isVisitingVariable(suffix)) {
            if (Logger.isErrorEnabled()) {
                Logger.log(VariableResolver.class.getName(), "resolve", Logger.LogType.ERROR, "Suffix=" + suffix + " contains cycles in variable definitions.");
            }
            throw new PatternException(suffix + " contains cycles in variable definitions.");
        }

        // Retrieve the pattern from the config map and resolve the variable.
        final String varPattern = context.getVariablePattern(suffix);
        if (varPattern != null) {
            context.visitVariableStart(suffix); // Housekeeping for cycle check
            
            try { 
                final ResolvedValue rv= context.resolve(varPattern);

                context.visitVariableEnd(); // Housekeeping for cycle check
                if (rv.isFullyResolved()) {
                    value = rv.getValue();
                    // Cache the resolved value.
                    context.setResolvedVariable(suffix, value);
                    if (Logger.isDebugEnabled()) {
                        Logger.log(VariableResolver.class.getName(), "resolve", Logger.LogType.DEBUG, "Result=" + value);
                    }
                    return value;
                }
            } 
            /* If can't resolve, check if there is a default value
               to return. If not, re-throw exception. */
            catch(PatternException e) { 
                if ( defaultValue == null ) { 
                    if (Logger.isErrorEnabled()) {
                        Logger.log(VariableResolver.class.getName(), "resolve", Logger.LogType.ERROR, suffix + "Default value is null and caught PatternException " + e.toString());
                    }
                    throw e; 
                } 
                else { 
                    if (Logger.isDebugEnabled()) {
                        Logger.log(VariableResolver.class.getName(), "resolve", Logger.LogType.DEBUG, "Return defaultValue=" + defaultValue);
                    }
                    return defaultValue; 
                }
            } 
        }
        /* If can't resolve, check if there is a default value
               to return. If not, throw exception. */        
        if ( defaultValue == null ) { 
            if (Logger.isErrorEnabled()) {
                Logger.log(VariableResolver.class.getName(), "resolve", Logger.LogType.ERROR, suffix + "Default value is null. Cannot resolve " + suffix);
            }
            throw new PatternException("can not resolve " + suffix);
        } 
        else { 
            if (Logger.isDebugEnabled()) {
                Logger.log(VariableResolver.class.getName(), "resolve", Logger.LogType.DEBUG, "Return defaultValue=" + defaultValue);
            }
            return defaultValue; 
        }
    }
}
