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

import application.rest.v1.json.JSONPath;
import application.rest.v1.json.JSONPathParser;

import com.ibm.kappnav.logging.Logger;

// ${resource.<json-path>} (only supports child-axis; dot and bracket notation)
public final class ResourceResolver implements Resolver {
    
    @Override
    public String getName() {
        return "resource";
    }
    
    @Override
    public String resolve(ResolutionContext context, String suffix) throws PatternException {
        if (Logger.isErrorEnabled()) {
            Logger.log(ResourceResolver.class.getName(), "resolve", Logger.LogType.ERROR, "For suffix=" + suffix);
        }
        final JSONPathParser parser = new JSONPathParser();
        final JSONPath path = parser.parse(suffix);
        if (path != null) {
            return path.resolveLeaf(context.getResource());
        } else {
            if (Logger.isErrorEnabled()) {
                Logger.log(ResourceResolver.class.getName(), "resolve", Logger.LogType.ERROR, "Path is null. Cannot resolve resource for suffix=" + suffix);
            }
            throw new PatternException("Cannot resolve resource " + suffix);
        }
    }
}
