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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import application.rest.v1.actions.ResolutionContext.ResolvedValue;
import com.ibm.kappnav.logging.Logger;

// ${func.<function-name>(<parameter1>,<parameter2>,etc...>}
// e.g. ${func.podlist(${resource.metata.name})}
public final class FunctionResolver implements Resolver {
    
    private static final Map<String,Function> functions;
    static {
        functions = new HashMap<>();
        addFunction(new KubectlGetFunction());
        addFunction(new PodlistFunction());
        addFunction(new AppPodlistFunction());
        addFunction(new ReplicaSetFunction());
    }
    
    private static void addFunction(Function function) {
        final String name = function.getName();
        if (!functions.containsKey(name)) {
            functions.put(name, function);
        }
    }

    @Override
    public String getName() {
        return "func";
    }
    
    @Override
    public String resolve(ResolutionContext context, String suffix) throws PatternException {
        if (Logger.isEntryEnabled()) {
            Logger.log(FunctionResolver.class.getName(), "resolve", Logger.LogType.ENTRY, "For suffix=" + suffix);
        }
        final FunctionOrSnippetTokenizer tokenizer = new FunctionOrSnippetTokenizer(suffix);
        final String functionName = tokenizer.getName();
        if (functionName != null) {
            // Resolve function.
            final Function function = functions.get(functionName);
            if (function == null || !function.allowedParameterCount(tokenizer.getParameterCount())) {
                // No matching function was found in the map.
                throw new PatternException("can not resolve " + suffix + " because no matching function found in the map");
            }
            
            // Resolve parameters.
            List<String> parameters = new ArrayList<>();
            for (String parameter : tokenizer) {
                final ResolvedValue rv = context.resolve(parameter);
                if (rv.isFullyResolved()) {
                    parameters.add(rv.getValue());
                }
                // One of the function parameters couldn't be resolved.
                else {
                    if (Logger.isExitEnabled()) {
                        Logger.log(FunctionResolver.class.getName(), "resolve", Logger.LogType.EXIT, "Return null.");
                    }
                    return null;
                }
            }
            
            // Invoke the function.
            final String result = function.invoke(context, parameters);
            if (Logger.isExitEnabled()) {
                Logger.log(FunctionResolver.class.getName(), "resolve", Logger.LogType.EXIT, "Result=" + result);
            }
            return result;
        }
        if (Logger.isErrorEnabled()) {
            Logger.log(FunctionResolver.class.getName(), "resolve", Logger.LogType.ERROR, "Cannot resolve " + suffix + " because function name is null");
        }
        throw new PatternException("can not resolve " + suffix + " because function name is null");
    }
}
