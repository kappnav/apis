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

package application.rest.v1.configmaps;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import application.rest.v1.KAppNavConfig;
import application.rest.v1.KAppNavEndpoint;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;

import com.ibm.kappnav.logging.Logger;

public class ConfigMapProcessor {

    public enum ConfigMapType {
        ACTION,
        STATUS_MAPPING
    }

    private static final String className = ConfigMapProcessor.class.getName();

    private static final String GLOBAL_NAMESPACE = KAppNavConfig.getkAppNavNamespace();

    private static final String ACTION_CONFIG_MAP_NAME = "kappnav.actions.";
    private static final String STATUS_MAPPING_CONFIG_MAP_NAME = "kappnav.status-mapping.";
    private static final String UNREGISTERED = "kappnav.status-mapping-unregistered";

    private static final String METADATA_PROPERTY_NAME = "metadata";
    private static final String ANNOTATIONS_PROPERTY_NAME = "annotations";

    private static final String KAPPNAV_ACTIONS_ON_CONFLICT_PROPERTY_NAME = "kappnav.actions.on.conflict";

    private final String actionNameWithKind;
    private final String statusMappingNameWithKind;
    private final Map<String,JsonObject> kappnavNSMapCache;

    public ConfigMapProcessor(String kind) {
        this.actionNameWithKind = ACTION_CONFIG_MAP_NAME + kind.toLowerCase(Locale.ENGLISH);
        this.statusMappingNameWithKind = STATUS_MAPPING_CONFIG_MAP_NAME + kind.toLowerCase(Locale.ENGLISH);
        this.kappnavNSMapCache = new HashMap<>();
    }

    public JsonObject getConfigMap(ApiClient client, JsonObject component, ConfigMapType type) {
        final ConfigMapBuilder builder = type == ConfigMapType.ACTION ? new ActionConfigMapBuilder() : new StatusMappingConfigMapBuilder();
        final String subkind = KAppNavEndpoint.getComponentSubKind(component);
        final String name = KAppNavEndpoint.getComponentName(component);
        
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "For component subkind=" + subkind + ", component name=" + name);
        }

        // Map: kappnav.actions.{kind}[-{subkind}].{name}
        if (name != null && !name.isEmpty()) {
            final String namespace = KAppNavEndpoint.getComponentNamespace(component);
            JsonObject map = getConfigMap(client, namespace, getConfigMapName(type, 
                    (subkind != null && !subkind.isEmpty()) ? '-' + subkind + '.' + name : '.' + name));
            if (map != null) {
                builder.merge(map);
                // Stop here if the action is replace.
                if (getConflictAction(map) == ConflictAction.REPLACE) {
                    return builder.getConfigMap();
                }
            } else {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "Map-1 is null.");
                }
            }
        }

        // Map: kappnav.actions.{kind}-{subkind}
        if (subkind != null && !subkind.isEmpty()) {
            JsonObject map = getConfigMap(client, GLOBAL_NAMESPACE, getConfigMapName(type, '-' + subkind));
            if (map != null) {
                builder.merge(map);
                // Stop here if the action is replace.
                if (getConflictAction(map) == ConflictAction.REPLACE) {
                    return builder.getConfigMap();
                }
            } else {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "Map-2 is null.");
                }
            }
        }

        // Map: kappnav.actions.{kind}
        JsonObject map = getConfigMap(client, GLOBAL_NAMESPACE, getConfigMapName(type, ""));
        if (map != null) {
            builder.merge(map);
        } else {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "Map-3 is null.");
            }
        }

        if (type == ConfigMapType.STATUS_MAPPING && builder.getConfigMap().entrySet().size() == 0) {
            // unregistered, try the unregistered configmap
            map = getConfigMap(client, GLOBAL_NAMESPACE, getUnregisteredConfigMapName());
            if (map != null) {
                builder.merge(map);
            } else {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "Map-4 is null.");
                }
            }
        }

        return builder.getConfigMap();
    }

    private String getConfigMapName(ConfigMapType type, String suffix) {
        return (type == ConfigMapType.ACTION ? actionNameWithKind : statusMappingNameWithKind) + suffix;
    }
    
    private String getUnregisteredConfigMapName() {
        return UNREGISTERED;
    }

    private JsonObject getConfigMap(ApiClient client, String namespace, String configMapName) {
        // Return the map from the local cache if it's been previously loaded.
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "For namespace=" + namespace + ", configMapName=" + configMapName);
        }

        final boolean isGlobalNS = GLOBAL_NAMESPACE.equals(namespace);
        if (isGlobalNS && kappnavNSMapCache.containsKey(configMapName)) {
            return kappnavNSMapCache.get(configMapName);
        }
        try {
            CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);

            V1ConfigMap map = api.readNamespacedConfigMap(configMapName, namespace, null, null, null);
            final JsonElement element = client.getJSON().getGson().toJsonTree(map);
            if (element != null && element.isJsonObject()) {
                final JsonObject m = element.getAsJsonObject();
                if (isGlobalNS) {
                    // Store the map in the local cache.
                    kappnavNSMapCache.put(configMapName, m);
                }
                return m;
            }
        }
        catch (ApiException e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "Caught ApiException " + e.toString());
            }
        }

        if (isGlobalNS) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "Global namespace, store null in the local cache");
            }
            // No map. Store null in the local cache.
            kappnavNSMapCache.put(configMapName, null);
        }
        return null;
    }

    private ConflictAction getConflictAction(JsonObject map) {
        final JsonObject metadata = map.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            final JsonObject annotations = metadata.getAsJsonObject(ANNOTATIONS_PROPERTY_NAME);
            if (annotations != null) {
                JsonElement e = metadata.get(KAPPNAV_ACTIONS_ON_CONFLICT_PROPERTY_NAME);
                if (e != null && e.isJsonPrimitive()) {
                    String s = e.getAsString();
                    if ("merge".equals(s)) {
                        return ConflictAction.MERGE;
                    }
                    else if ("replace".equals(s)) {
                        return ConflictAction.REPLACE;
                    }
                }   
            }
        }
        // Default value
        return ConflictAction.MERGE;
    }

    enum ConflictAction {
        MERGE,
        REPLACE
    }
}
