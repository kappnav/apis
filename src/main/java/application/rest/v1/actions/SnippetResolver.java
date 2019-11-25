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
import java.util.List;
import java.lang.System;

import application.rest.v1.actions.ResolutionContext.ResolvedValue;

// ${snippet.<snippet-name>(<parameter1>,<parameter2>,etc...>}
// e.g. ${snippet.create-kibana-log-url(${builtin.kibana-url},${func.podlist(${resource.metadata.name})})}
public class SnippetResolver implements Resolver {

    @Override
    public String getName() {
        return "snippet";
    }
    
    @Override
    public String resolve(ResolutionContext context, String suffix) throws PatternException {
        final FunctionOrSnippetTokenizer tokenizer = new FunctionOrSnippetTokenizer(suffix);
        final String snippetName = tokenizer.getName();
        if (snippetName != null) {
            // Resolve snippet.
            final String snippet = context.getSnippet(snippetName);
            if (snippet == null) {
                // No snippet was found in the action config map.
                throw new PatternException("Snippet " + snippetName + " is not found in the action config map");
            }
            
            // Resolve parameters.
            List<String> parameters = new ArrayList<>();
            for (String parameter : tokenizer) {
                final ResolvedValue rv = context.resolve(parameter);
                if (rv.isFullyResolved()) {
                    parameters.add(rv.getValue());
                }
                // One of the script parameters couldn't be resolved.
                // Stop here instead of invoking the script with a
                // 'bad' parameter.
                else {
                    throw new PatternException("One or more of the script parameters can not be resolved");
                }
            }           
            context.invokeSnippet(snippet, parameters);
        }
        throw new PatternException("Can not resolve " + suffix + " because snippet name is null");
    }
}
