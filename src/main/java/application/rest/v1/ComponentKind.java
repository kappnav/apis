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
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.kappnav.logging.Logger;

import io.kubernetes.client.openapi.ApiClient;

/**
 * Kubernetes component kind.
 */
public final class ComponentKind {
    
    public final String group;
    public final String kind;

    private static final String className = ComponentKind.class.getName();

    public ComponentKind(String group, String kind) {
        this.group = group;
        this.kind = kind;
    }
    
    public String toString() {
        return "{ Group: " + group  + ", Kind: " + kind + " }";
    }
    
    public static List<ComponentKind> getComponentKinds(ApiClient client, Object application, ComponentInfoRegistry registry) {
        return getComponentKinds(client.getJSON().getGson().toJsonTree(application), registry);
    }
    
    public static List<ComponentKind> getComponentKinds(final JsonElement element, ComponentInfoRegistry registry) {
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
                                ComponentKind ck = new ComponentKind(groupString, kind.getAsString());
                                if (Logger.isInfoEnabled()) {
                                    Logger.log(className, "getComponentKinds", Logger.LogType.INFO, "processing componentKind group: " + groupString + " kind: " +  kind.getAsString());
                                }
                                // validate group and kind
                                Set<String> apiVersions = registry.getComponentGroupApiVersions(ck);
                                if (apiVersions != null && !apiVersions.isEmpty()) {
                                    for (String apiVersion : apiVersions) {
                                        if (apiVersion != null) {
                                            if (Logger.isInfoEnabled()) {
                                                Logger.log(className, "getComponentKinds", Logger.LogType.INFO, "found apiVersion " + apiVersion + " for componentKind group: " + groupString + " kind: " +  kind.getAsString());
                                            }
                                            String[] substring = apiVersion.split("/");
                                            // For backwards compatibility, invalid group name case,
                                            // use the group of the default apiversion 
                                            if (!substring[0].equals(groupString)) {
                                                ck = new ComponentKind(substring[0], kind.getAsString());
                                                Logger.log(className, "getComponentKinds", Logger.LogType.INFO, "Substituting group: " + substring[0] + " for group: " + groupString + " with kind: " + kind.getAsString());
                                            }
                                            result.add(ck);
                                        }
                                        else {
                                            if (Logger.isInfoEnabled()) {
                                                Logger.log(className, "getComponentKinds", Logger.LogType.INFO, "apiVersion null for componentKind group: " + groupString + " kind: " +  kind.getAsString() + " skipping");
                                            }
                                        }
                                        break;
                                    }
                                }
                                else {
                                    if (Logger.isInfoEnabled()) {
                                        Logger.log(className, "getComponentKinds", Logger.LogType.INFO, "componentKind group: " + groupString + " kind: " +  kind.getAsString() + " not recognized. skipping");
                                    }
                                }
                            }
                        }
                    });
                }
            }
        }
        return result;
    }
}