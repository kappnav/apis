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

// kubectlGet(<arg1>,<arg2>,etc...)
// Invokes "kubectl get <arg1> <arg2> etc..."
public class KubectlGetFunction extends CommandFunction {
    
    private static final String KUBECTL = "kubectl";
    private static final String GET = "get";

    @Override
    public String getName() {
        return "kubectlGet";
    }
    
    @Override
    public boolean allowedParameterCount(int parameterCount) {
        return parameterCount > 0;
    }

    @Override
    public String invoke(ResolutionContext context, List<String> parameters) {
        final int length = parameters.size();
        final String[] params = parameters.toArray(new String[length]);
        
        final String[] commandArgs = new String[length + 2];
        commandArgs[0] = KUBECTL;
        commandArgs[1] = GET;
        System.arraycopy(params, 0, commandArgs, 2, length);
        try {
            String result = invoke(context, commandArgs);
            if (result == null) {
                throw new PatternException("Kubectl get " + parameters + " can not be resolved");
            }
            return result;
        } catch (Exception e) {
            throw new PatternException("Kubectl get " + parameters + " can not be resolved");
        }
    }
}
