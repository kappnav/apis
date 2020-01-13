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

package application.rest.v1.json;

import java.util.Collections;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.ibm.kappnav.logging.Logger;

// Subset of JSON Path (only supports child-axis; dot and bracket notation).
public class JSONPath {
    
    private final List<String> children;
    
    JSONPath (List<String> children) {
        this.children = (children != null) ? children : Collections.emptyList();
    }
    
    public String resolveLeaf(final JsonObject context) {
        if (!children.isEmpty()) {
            JsonObject o = context;
            // Walk to the JsonObject that contains the leaf.
            final int end = children.size() - 1;
            for (int i = 0; i < end; ++i) {
                JsonElement e = o.get(children.get(i));
                if (e != null && e.isJsonObject()) {
                    o = e.getAsJsonObject();
                    Logger.log(JSONPath.class.getName(), "resolveLeaf", Logger.LogType.DEBUG, "JsonObject=" +  o.toString());
                }
                else {
                    return null;
                }
            }
            // Return the value of the leaf node.
            final JsonElement result = o.get(children.get(end));
            if (result != null && result.isJsonPrimitive()) {
                return result.getAsString();
            }
        }
        return null;
    }
}
