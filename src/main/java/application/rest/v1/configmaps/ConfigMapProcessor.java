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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.kappnav.logging.Logger;

import application.rest.v1.KAppNavConfig;
import application.rest.v1.KAppNavEndpoint;
import io.kubernetes.client.ApiClient;

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
        final String namespace = KAppNavEndpoint.getComponentNamespace(component);        
        final String kind = KAppNavEndpoint.getComponentKind(component);
        final String apiVersion = KAppNavEndpoint.getComponentApiVersion(component, kind);
        final String subkind = KAppNavEndpoint.getComponentSubKind(component);
        final String name = KAppNavEndpoint.getComponentName(component);
        JsonObject map = null;

        if (Logger.isDebugEnabled()) {
            Logger.log(className, "getConfigMap", Logger.LogType.DEBUG,
                       "\n type = " + type +
                       "\n component namespace = " + namespace +
                       "\n component apiVersion = " + apiVersion +
                       "\n component name= " + name +
                       "\n component subkind= " + subkind +
                       "\n component kind = " + kind);
        }

        if ((apiVersion == null) || (apiVersion.isEmpty())) {
            if (Logger.isDebugEnabled()) 
                Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "component apiVersion is null or empty.");
        } else {            
            if (type == ConfigMapType.ACTION) {  // using KAM CRs for action configmaps
                KindActionMappingProcessor kam =
                    new KindActionMappingProcessor(namespace, apiVersion, name, subkind, kind);
                String configMapName = getConfigMapName(type, name, subkind, kind);
                map = getConfigMap(client, kam, namespace, configMapName, builder);
            } else { // status mapping configmap       
                if (type == ConfigMapType.STATUS_MAPPING && builder.getConfigMap().entrySet().size() == 0) {
                    // unregistered, try the unregistered configmap
                    map = getConfigMap(client, null, GLOBAL_NAMESPACE, getUnregisteredConfigMapName(), builder);
                    if (map != null) {
                        builder.merge(map);
                    } else {
                        if (Logger.isDebugEnabled()) 
                            Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "configmap is null.");
                    }
                }
            }
        }

        System.out.println("EXIT: builder.getConfigMap()" + builder.getConfigMap());
        return builder.getConfigMap();
    }

    private String getConfigMapName(ConfigMapType type, String name, String subkind, String kind) {
        String suffix;
        if (name != null && !name.isEmpty()) {
            suffix = (subkind != null && !subkind.isEmpty()) ? '-' + subkind + '.' + name : '.' + name;
        } else if (subkind != null && !subkind.isEmpty()) {
            suffix = '-' + subkind;
        } else {
            suffix = "";
        }
        
        return (type == ConfigMapType.ACTION ? actionNameWithKind : statusMappingNameWithKind) + suffix;
    }
    
    private String getUnregisteredConfigMapName() {
        return UNREGISTERED;
    }

    private JsonObject getConfigMap(ApiClient client, KindActionMappingProcessor kam, 
                                    String namespace, String configMapName, ConfigMapBuilder builder) {
        // Return the map from the local cache if it's been previously loaded.
        if (Logger.isEntryEnabled()) {
            Logger.log(className, "getConfigMap", Logger.LogType.ENTRY, "For namespace=" + namespace + 
                       ", configMapName=" + configMapName);
        }

        final boolean isGlobalNS = GLOBAL_NAMESPACE.equals(namespace);
        if (isGlobalNS && kappnavNSMapCache.containsKey(configMapName)) {
            return kappnavNSMapCache.get(configMapName);
        }
        
        if (kam != null) {
            // get Configmaps declared in the KindActionMapping custom resources
            ArrayList <String> configMapsList = kam.getConfigMapsFromKAMs(client);

            if (configMapsList != null) {
                // look up the configmaps in a cluster
                final ArrayList<JsonObject> configMapsFound = ConfigMapCache.getConfigMapsAsJSON(client, configMapsList);

                // merge configmaps found
                mergeConfigMaps(configMapsFound, builder);
                JsonObject map = builder.getConfigMap();
                if (map != null) {
                    if (isGlobalNS) {
                        // Store the map in the local cache.
                        kappnavNSMapCache.put(configMapName, map); 
                    }
                    if (Logger.isExitEnabled()) 
                        Logger.log(className, "getConfigMap", Logger.LogType.EXIT, "Merged configmap returned = " + map);
                    return map;
                } else {
                    if (Logger.isDebugEnabled()) 
                        Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "map to be merged is null");
                }   
            } else {
                if (Logger.isDebugEnabled()) 
                Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "no configmap with given kam is found");
            } 
        } else {
            final JsonElement element = ConfigMapCache.getConfigMapAsJSON(client, namespace, configMapName);
            if (element != null && element.isJsonObject()) {
                final JsonObject map = element.getAsJsonObject();
                if (isGlobalNS) {
                    // Store the map in the local cache.
                    kappnavNSMapCache.put(configMapName, map);
                }
                if (Logger.isExitEnabled()) 
                    Logger.log(className, "getConfigMap", Logger.LogType.EXIT, "Status mapping configmap returned = " + map);
                return map;
            }
        }
        
        if (isGlobalNS) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, 
                           "Global namespace, store null in the local cache");
            }
            // No map. Store null in the local cache.
            kappnavNSMapCache.put(configMapName, null);
        }

        if (Logger.isExitEnabled()) 
            Logger.log(className, "getConfigMap", Logger.LogType.EXIT, "returns null");
        return null;
    }

    private static ConflictAction getConflictAction(JsonObject map) {
        final JsonObject metadata = map.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            final JsonObject annotations = metadata.getAsJsonObject(ANNOTATIONS_PROPERTY_NAME);
            if (annotations != null) {
                JsonElement e = metadata.get(KAPPNAV_ACTIONS_ON_CONFLICT_PROPERTY_NAME);
                if (e != null && e.isJsonPrimitive()) {
                    String s = e.getAsString();
                    if ("merge".equals(s)) {
                        if (Logger.isDebugEnabled()) 
                            Logger.log(className, "getConflictAction", Logger.LogType.DEBUG, "MERGE");
                        return ConflictAction.MERGE;
                    }
                    else if ("replace".equals(s)) {
                        if (Logger.isDebugEnabled()) 
                            Logger.log(className, "getConflictAction", Logger.LogType.DEBUG, "REPLACE");
                        return ConflictAction.REPLACE;
                    }
                }   
            }
        }

        // Default value
        if (Logger.isDebugEnabled()) 
            Logger.log(className, "getConflictAction", Logger.LogType.DEBUG, "MERGE");
        return ConflictAction.MERGE;
    }

    enum ConflictAction {
        MERGE,
        REPLACE
    }

    private static void mergeConfigMaps(ArrayList<JsonObject> configMapsFound, ConfigMapBuilder builder) {
        for (int cIdx=0; cIdx<configMapsFound.size(); cIdx++) {
            JsonObject cMap = configMapsFound.get(cIdx);
            if (cMap != null) {
                if (Logger.isDebugEnabled()) 
                    Logger.log(className, "getConflictAction", Logger.LogType.DEBUG, 
                               "configmap to be merged:\n cMap["+cIdx+"]="+cMap);
                builder.merge(cMap);

                // Stop here if the action is replace.
                if (getConflictAction(cMap) == ConflictAction.REPLACE) {
                    if (Logger.isDebugEnabled()) 
                        Logger.log(className, "getConflictAction", Logger.LogType.DEBUG, 
                                   "Stop merging with a REPLACE conflicit action");
                    return;
                }
            } else {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "mergeConfigMapActions", Logger.LogType.DEBUG, 
                               "Do nothing as the configMap to be merged is null.");
                }
            }
        }
    }
}
