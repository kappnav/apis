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

import javax.xml.namespace.QName;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.kappnav.logging.Logger;

import application.rest.v1.KAppNavConfig;
import application.rest.v1.KAppNavEndpoint;
import io.kubernetes.client.ApiClient;

public class ConfigMapProcessor {

    public static enum ConfigMapType {
        ACTION,
        STATUS_MAPPING,
        SECTION
    }

    private static final String className = ConfigMapProcessor.class.getName();

    public static final String GLOBAL_NAMESPACE = KAppNavConfig.getkAppNavNamespace();

    private static final String ACTION_CONFIG_MAP_NAME = "kappnav.actions.";
    private static final String STATUS_MAPPING_CONFIG_MAP_NAME = "kappnav.status-mapping.";
    public static final String UNREGISTERED = "kappnav.status-mapping-unregistered";

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
        if (Logger.isEntryEnabled()) 
            Logger.log(className, "getConfigMap", Logger.LogType.ENTRY, "ConfigMapType = " + type.toString());

        final ConfigMapBuilder builder = type == ConfigMapType.ACTION ? new ActionConfigMapBuilder() : new StatusMappingConfigMapBuilder();   
        final String namespace = KAppNavEndpoint.getComponentNamespace(component);        
        final String kind = KAppNavEndpoint.getComponentKind(component);
        final String apiVersion = KAppNavEndpoint.getComponentApiVersion(component, kind);
        final String subkind = KAppNavEndpoint.getComponentSubKind(component);
        final String name = KAppNavEndpoint.getComponentName(component);
        final OwnerRef[] owners = KAppNavEndpoint.getOwners(component);
        JsonObject map = null;

        if (Logger.isDebugEnabled()) {
            Logger.log(className, "getConfigMap", Logger.LogType.DEBUG,
                       "\n component namespace = " + namespace +
                       "\n component apiVersion = " + apiVersion +
                       "\n component name = " + name +
                       "\n component subkind= " + subkind +
                       "\n component kind = " + kind + 
                       "\n component ownerReferences = " + ownerRefArrayToString(owners));
        }

        if ((apiVersion == null) || (apiVersion.isEmpty())) {
            if (Logger.isDebugEnabled()) 
                Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "component apiVersion is null or empty.");
        } else { 
            // Get configmaps available in a cluster
            KindActionMappingProcessor kam =
                new KindActionMappingProcessor(namespace, owners, apiVersion, name, subkind, kind);
            String configMapName = getConfigMapName(type, name, subkind, kind);
            map = getConfigMap(client, kam, namespace, type, configMapName, builder);
 
            if (type == ConfigMapType.STATUS_MAPPING && builder.getConfigMap().entrySet().size() == 0) {
                // unregistered, try the unregistered configmap
                map = getConfigMap(client, null, GLOBAL_NAMESPACE, type, getUnregisteredConfigMapName(), builder);
                if (map != null) {
                    builder.merge(map);
                } else {
                    if (Logger.isDebugEnabled()) 
                        Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "configmap is null.");
                }
            }
        }

        JsonObject configMapFound = builder.getConfigMap();
        if (Logger.isExitEnabled()) 
            Logger.log(className, "getConfigMap", Logger.LogType.EXIT, "configMap found = " + configMapFound);
        return configMapFound;
    }

    /**
     * Return printable version of ownerRef array in this format: 
     * 
     * Return string "null" if input array is null 
     * Return string "empty" if input array is empty 
     * Otherwise return array as string "[ element1 ][elemnt 2] ..."
     * */ 
    protected static String ownerRefArrayToString(OwnerRef[] array) { 
        String string= null;
        if ( array == null ) { 
            string= "null";
        }
        else if ( array.length == 0 ) { 
            string= "empty";
        }
        else { 
            for (int i=0; i < array.length; i++) { 
                string= (string == null) ? "[" + array[i] + "]" : string + "[" + array[i] + "]"; 
            } 
        }
        return string; 
    }

    private String getConfigMapName(ConfigMapType type, String name, String subkind, String kind) {
        String suffix = getConfigMapNameSuffix(name, subkind);        
        return (type == ConfigMapType.ACTION ? actionNameWithKind : statusMappingNameWithKind) + suffix;
    }

    public static String getConfigMapNameSuffix(String name, String subkind) {
        String suffix;
        if (name != null && !name.isEmpty()) {
            suffix = (subkind != null && !subkind.isEmpty()) ? '-' + subkind + '.' + name : '.' + name;
        } else if (subkind != null && !subkind.isEmpty()) {
            suffix = '-' + subkind;
        } else {
            suffix = "";
        }       
        return suffix;
    }
    
    private String getUnregisteredConfigMapName() {
        return UNREGISTERED;
    }

    public JsonObject getConfigMap(ApiClient client, KindActionMappingProcessor kam, String namespace,
                                    ConfigMapType type, String configMapName, ConfigMapBuilder builder) {      
        if (Logger.isEntryEnabled()) {
            Logger.log(className, "getConfigMap", Logger.LogType.ENTRY, "For namespace=" + namespace + 
                       ", configMapName=" + configMapName);
        }

        // Return the map from the local cache if it's been previously loaded.
        final boolean isGlobalNS = GLOBAL_NAMESPACE.equals(namespace);
        if (isGlobalNS && kappnavNSMapCache.containsKey(configMapName)) {
            return kappnavNSMapCache.get(configMapName);
        }
        
        if (kam != null) {
            // get Configmaps declared in the KindActionMapping custom resources
            ArrayList <QName> configMapsList = kam.getConfigMapsFromKAMs(client, type);

            if (configMapsList != null) {
                // look up the configmaps in a cluster
                final ArrayList<JsonObject> configMapsFound = ConfigMapCache.getConfigMapsAsJSON(client, configMapsList);

                // merge configmaps found
                mergeConfigMaps(configMapsFound, type, builder);
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
            Logger.log(className, "getConfigMap", Logger.LogType.EXIT, "No map so storing null to local cache.");
        return null;
    }

    private static ConflictAction getConflictAction(JsonObject map) {
        final JsonObject metadata = map.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            final JsonObject annotations = metadata.getAsJsonObject(ANNOTATIONS_PROPERTY_NAME);
            if (annotations != null) {
                JsonElement e = annotations.get(KAPPNAV_ACTIONS_ON_CONFLICT_PROPERTY_NAME);               
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

    protected static void mergeConfigMaps(ArrayList<JsonObject> configMapsFound, ConfigMapType type, ConfigMapBuilder builder) {
        String methodName = "mergeConfigMaps";
        if (type == ConfigMapType.STATUS_MAPPING){
            // Use the first configmap found with no more merge
            if ( !configMapsFound.isEmpty()) {
                JsonObject cMap = configMapsFound.get(0);
                if (cMap != null)  {
                    if (Logger.isDebugEnabled()) 
                        Logger.log(className, methodName, Logger.LogType.DEBUG, 
                                   "Status mapping: configmap to be merged:\n cMap[0] = "+cMap);
                    builder.merge(cMap);
                    return;
                }
            }
        }

        // action and sections: merge the configmaps
        for (int cIdx=0; cIdx<configMapsFound.size(); cIdx++) {
            JsonObject cMap = configMapsFound.get(cIdx);
            if (cMap != null) {
                if (Logger.isDebugEnabled()) 
                    Logger.log(className, methodName, Logger.LogType.DEBUG, 
                               "Action: configmap to be merged:\n cMap["+cIdx+"] = "+cMap);
                builder.merge(cMap);

                // Stop here if the action is replace.
                if (getConflictAction(cMap) == ConflictAction.REPLACE) {
                    if (Logger.isDebugEnabled()) 
                        Logger.log(className, methodName, Logger.LogType.DEBUG, 
                                   "Stop merging with a REPLACE conflict action");
                    return;
                }
            } else {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.DEBUG, 
                               "Do nothing as the configMap to be merged is null.");
                }
            }
        }
    }
}
