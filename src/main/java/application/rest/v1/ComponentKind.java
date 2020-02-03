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

package application.rest.v1;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.kubernetes.client.ApiClient;

/**
 * Kubernetes component kind.
 */
public final class ComponentKind {
    
    public final String group;
    public final String kind;
    
    public ComponentKind(String group, String kind) {
        this.group = group;
        this.kind = kind;
    }
    
    public String toString() {
        return "{ Group: " + group  + ", Kind: " + kind + " }";
    }
    
    public static List<ComponentKind> getComponentKinds(ApiClient client, Object application) {
        return getComponentKinds(client.getJSON().getGson().toJsonTree(application));
    }
    
    public static List<ComponentKind> getComponentKinds(final JsonElement element) {
        List<ComponentKind> result = new ArrayList<>();
        if (element != null && element.isJsonObject()) {
            JsonObject root = element.getAsJsonObject();
            JsonElement spec = root.get("spec");
            if (spec != null && spec.isJsonObject()) {
                JsonObject specObj = spec.getAsJsonObject();
                JsonElement componentKinds = specObj.get("componentKinds");
                if (componentKinds != null && componentKinds.isJsonArray()) {
                    JsonArray componentArray = componentKinds.getAsJsonArray();
                    componentArray.forEach(v -> {
                        if (v != null && v.isJsonObject()) {
                            JsonObject componentKind = v.getAsJsonObject();
                            JsonElement group = componentKind.get("group");
                            JsonElement kind = componentKind.get("kind");
                            if (kind != null) {
                                String groupString = group != null ? group.getAsString() : "";
                                result.add(new ComponentKind(groupString, kind.getAsString()));
                            }
                        }
                    });
                }
            }
        }
        return result;
    }
}