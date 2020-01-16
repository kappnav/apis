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

import com.ibm.kappnav.logging.Logger;

// ${global.<configmap-name>#<configmap-field>}
public final class GlobalResolver implements Resolver {

    @Override
    public String getName() {
        return "global";
    }
    
    @Override
    public String resolve(ResolutionContext context, String suffix) throws PatternException {
        int i = suffix.indexOf('#');
        if (i >= 0) {
            final String mapName = suffix.substring(0, i);
            final String mapField = suffix.substring(i + 1);
            return context.getConfigMapDataField(mapName, mapField);
        }
        if (Logger.isErrorEnabled()) {
            Logger.log(GlobalResolver.class.getName(), "resolve", Logger.LogType.ERROR, "Cannot resolve " + suffix + " because syntax is incorrect: expected '#' not found.");
        }
        throw new PatternException("cannot resolve " + suffix + " because syntax is incorrect: expected '#' not found.");
    }
}
