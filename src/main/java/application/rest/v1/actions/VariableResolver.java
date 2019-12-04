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

// ${var.<variable-name>}
// e.g. ${var.nodePort}
public class VariableResolver implements Resolver {

    @Override
    public String getName() {
        return "var";
    }

    @Override
    public String resolve(ResolutionContext context, String suffix) throws PatternException {
        // Immediately return the value if the variable has been previously resolved.
        String value = context.getResolvedVariable(suffix);
        if (value != null) {
            return value;
        } 

        // Guard against cycles in variable definitions.
        // (e.g. var x = "${var.y}", var y = "${var.z}", var z = "${var.x}").
        if (context.isVisitingVariable(suffix)) {
            throw new PatternException(suffix + " contains cycles in variable definitions");
        }

        // Retrieve the pattern from the config map and resolve the variable.
        final String varPattern = context.getVariablePattern(suffix);
        if (varPattern != null) {
            context.visitVariableStart(suffix); // Housekeeping for cycle check
            final ResolvedValue rv = context.resolve(varPattern);
            context.visitVariableEnd(); // Housekeeping for cycle check
            if (rv.isFullyResolved()) {
                value = rv.getValue();
                // Cache the resolved value.
                context.setResolvedVariable(suffix, value);
                return value;
            } 
        }
    throw new PatternException("can not resolve " + suffix);
    }
}
