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
import com.google.gson.JsonArray;

import application.rest.v1.KAppNavConfig;
import application.rest.v1.KAppNavEndpoint;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;

import com.ibm.kappnav.logging.Logger;

public class SectionConfigMapProcessor {
    private static final String className = SectionConfigMapProcessor.class.getName();

    private static final String GLOBAL_NAMESPACE = KAppNavConfig.getkAppNavNamespace();

    private static final String SECTION_CONFIG_MAP_NAME = "kappnav.sections.";

    private static final String SECTIONS_PROPERTY_NAME = "sections";
    private static final String SECTION_DATASOURCES_PROPERTY_NAME = "section-datasources";
    private static final String SECTION_DATA_PROPERTY_NAME = "section-data";
    private static final String NAME_PROPERTY_NAME = "name"; 
    private static final String DATASOURCE_PROPERTY_NAME = "datasource";
    private static final String KINDS_PROPERTY_NAME = "kinds";   
    private static final String ENABLEMENT_LABEL_PROPERTY_NAME = "enablement-label";
    private static final String TYPE_PROPERTY_NAME = "type";
    private static final String LABELS_PROPERTY_NAME = "labels";
    private static final String METADATA_PROPERTY_NAME = "metadata";
    private static final String ANNOTATIONS_PROPERTY_NAME = "annotations";   
    private static final String LABEL_PREFIXES_PROPERTY_NAME = "label-prefixes";
    private static final String ANNOTATION_PREFIXES_PROPERTY_NAME = "annotation-prefixes";
    private static final String DATA_PROPERTY_NAME = "data";
    private static final String LABEL_PROPERTY_NAME = "label";
    private static final String ANNOTATION_PROPERTY_NAME = "annotation";
    private static final String VALUE_PROPERTY_NAME = "value";

            
    private final String sectionName;
    private final String sectionNameWithKind;
    
    private final Map<String,JsonObject> kappnavNSMapCache;

    public SectionConfigMapProcessor(String kind) {
        this.sectionName= SECTION_CONFIG_MAP_NAME;   
        this.sectionNameWithKind = SECTION_CONFIG_MAP_NAME + kind.toLowerCase(Locale.ENGLISH);
        this.kappnavNSMapCache = new HashMap<>();        
    }

    public JsonObject getConfigMap(ApiClient client, JsonObject component) {
        final SectionConfigMapBuilder builder = new SectionConfigMapBuilder();
        final String name = KAppNavEndpoint.getComponentName(component);
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "For component=" + name);
        }

        if (name != null && !name.isEmpty()) {       
            if (builder.getConfigMap().entrySet().size() == 0) {
                JsonObject map = getConfigMap(client, sectionNameWithKind);
                
                if (map != null) {
                    builder.merge(map);      
                } else {
                    if (Logger.isDebugEnabled()) {
                        Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "Map is null.");
                    }
                }
            }
        }
        
        return builder.getConfigMap();
    } 
         

    private JsonObject getConfigMap(ApiClient client, String configMapName) {
        // Return the map from the local cache if it's been previously loaded.
        if (kappnavNSMapCache.containsKey(configMapName)) {
            return kappnavNSMapCache.get(configMapName);
        }
        try {
            CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);
            
            V1ConfigMap map = api.readNamespacedConfigMap(configMapName, GLOBAL_NAMESPACE, null, null, null);
            final JsonElement element = client.getJSON().getGson().toJsonTree(map);
            if (element != null && element.isJsonObject()) {
                final JsonObject m = element.getAsJsonObject();   
                    
                // Store the map in the local cache.
                kappnavNSMapCache.put(configMapName, m);    
                return m;
            }
        }
        catch (ApiException e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "Caught ApiException " + e.toString());
            }
        }
        
        // No map. Store null in the local cache.
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "getConfigMap", Logger.LogType.DEBUG, "No map so storing null to local cache.");
        }
        kappnavNSMapCache.put(configMapName, null);
        return null;
    }


    // Process section config map into a map of sections and section-data output 
    // in the following format :
    //
    // {
    //   sections: []
    //   section-data: []
    // }
    //
    public JsonObject processSectionMap(ApiClient client, JsonObject component) {
        final JsonObject newSectionMap = new JsonObject();   
        
        // retreive section and section-datasource map 
        final JsonObject map = getConfigMap(client, component);                         
        final JsonElement sections = map.get(SECTIONS_PROPERTY_NAME);
        final JsonElement sectionDS = map.get(SECTION_DATASOURCES_PROPERTY_NAME);
        
        final JsonArray sectionDataResult = new JsonArray();
        
        if (sections != null && sections.isJsonArray()) {                   
            final JsonArray sectionArray = sections.getAsJsonArray();
            if (sectionArray.size() > 0) {                                                   
                sectionArray.forEach(v -> {
                    if (v != null && v.isJsonObject()) {
                        JsonObject sectionObject = v.getAsJsonObject();
                        String sectionName = sectionObject.getAsJsonPrimitive(NAME_PROPERTY_NAME).getAsString();
                        if (Logger.isDebugEnabled()) {
                            Logger.log(className, "processSectionMap", Logger.LogType.DEBUG, "Processing sectionName=" + sectionName);
                        }
                        //get resource component metadata
                        JsonObject metadata = component.getAsJsonObject(METADATA_PROPERTY_NAME);                            
                        //get metadata annotations and labels objects                          
                        JsonObject annoObj = metadata.getAsJsonObject(ANNOTATIONS_PROPERTY_NAME);
                        JsonObject labelObj = metadata.getAsJsonObject(LABELS_PROPERTY_NAME);
                                                       
                        // check if enablement-label is specified and if it is on resource
                        JsonElement enablementlabel = sectionObject.get(ENABLEMENT_LABEL_PROPERTY_NAME);
                        if (enablementlabel == null || enablementLabelInResource(enablementlabel, annoObj, labelObj)) { 
                            // get section-datasource name and check if it matches with section's datasource name                      
                            if (sectionDS != null && sectionDS.isJsonArray()) {
                                final JsonArray sectionDSArray = sectionDS.getAsJsonArray();
                                if (sectionDSArray.size() > 0) {
                                    sectionDSArray.forEach(s -> {
                                        if (s != null && s.isJsonObject()) {
                                            String dsName = sectionObject.getAsJsonPrimitive(DATASOURCE_PROPERTY_NAME).getAsString(); 
                                            JsonObject sectionDSObject = s.getAsJsonObject();
                                            String sectionDSName = sectionDSObject.getAsJsonPrimitive(NAME_PROPERTY_NAME).getAsString();
                                            if (Logger.isDebugEnabled()) {
                                                Logger.log(className, "processSectionMap", Logger.LogType.DEBUG, "Processing dsName=" + dsName + ", sectionDSName="+ sectionDSName);
                                            }

                                            if (dsName.equals(sectionDSName)) {   
                                                //get matching labels                                                       
                                                JsonArray matchLabels = getMatchingResources(sectionObject, sectionDSObject, labelObj, LABELS_PROPERTY_NAME);  
                                                //get matching annotations
                                                JsonArray matchAnnotations = getMatchingResources(sectionObject, sectionDSObject, annoObj, ANNOTATIONS_PROPERTY_NAME);  
                                                   
                                                if (matchAnnotations.size() > 0)
                                                    matchLabels.addAll(matchAnnotations);

                                                JsonObject  o = new JsonObject();
                                                o.addProperty(NAME_PROPERTY_NAME, dsName);
                                                if (matchLabels.size() > 0)  
                                                    o.add(DATA_PROPERTY_NAME, matchLabels);                                                    
                                                                                                                                                
                                                sectionDataResult.add(o);  
                                            }
                                        }
                                    });
                                }                                    
                            } 
                        } 
                    }                
                });
            }             
            newSectionMap.add(SECTIONS_PROPERTY_NAME, sections);
            newSectionMap.add(SECTION_DATA_PROPERTY_NAME, sectionDataResult);
        }
           
        return newSectionMap;                       
    }

    
    // check if enablement-label specified and if it is on resource
    private boolean enablementLabelInResource(JsonElement enablementLabel, JsonObject annoObj, JsonObject labelObj) {
        if (enablementLabel != null && enablementLabel.isJsonPrimitive()) {
            String enablementlabelStr = enablementLabel.getAsString(); 
                      
            if (annoObj != null) {
                JsonElement e = annoObj.get(enablementlabelStr);
                if (e != null && e.isJsonPrimitive()) {
                    return true;                   
                }
            } else {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "processSectionMap", Logger.LogType.DEBUG, "Annotation object is null.");
                }
            }

            if (labelObj != null) {
                JsonElement l = labelObj.get(enablementlabelStr);
                if (l!= null && l.isJsonPrimitive()) {
                    return true;
                }
            } else {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "processSectionMap", Logger.LogType.DEBUG, "Label object is null.");
                }
            }            
        }
        return false;                                                          
    }
  

    // get matching resources and matches with prefixes and annotations or labels array
    private JsonArray getMatchingResources (JsonObject sectionObject, JsonObject sectionDSObject, JsonObject resObj, String metadataType) {  
        if (Logger.isDebugEnabled()) {      
            Logger.log(className, "getMatchingResources", Logger.LogType.DEBUG, "For metadataType=" + metadataType);
        }
        final JsonArray matchResources = new JsonArray();
                                    
        JsonArray dsResourceArray = new JsonArray();
        JsonArray dsResourcePrefixes = new JsonArray(); 

        //get section-datasource type                                                                                             
        JsonElement type = sectionDSObject.get(TYPE_PROPERTY_NAME);
        if (type != null && type.isJsonPrimitive()) {
            String typeStr = type.getAsString();
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getMatchingResources", Logger.LogType.DEBUG, "Processing typeStr=" + typeStr);
            }
            //get type-specific fields such as "labels-annotations"
            if (typeStr.indexOf("-") > 0) { 
                String[] types = typeStr.split("-");
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "getMatchingResources", Logger.LogType.DEBUG, "Processing types=" + types.toString());
                }
                for (String t : types) {
                    JsonElement resourceE;
                    if (metadataType.equals(ANNOTATIONS_PROPERTY_NAME)) {
                        if (t.trim().equals(ANNOTATIONS_PROPERTY_NAME)) {
                            JsonElement annoE = sectionDSObject.get(ANNOTATIONS_PROPERTY_NAME);
                            if (annoE != null && annoE.isJsonArray()) {
                                dsResourceArray = annoE.getAsJsonArray();                           
                            }
                        }
                    } 
                    if  (metadataType.equals(LABELS_PROPERTY_NAME)) {
                        if (t.trim().equals(LABELS_PROPERTY_NAME)) {
                            JsonElement labelE = sectionDSObject.get(LABELS_PROPERTY_NAME);                       
                            if (labelE != null && labelE.isJsonArray()) {
                                dsResourceArray = labelE.getAsJsonArray();                           
                            }
                        }
                    }                   
                }                   
            } else { //single type 
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, "getMatchingResources", Logger.LogType.DEBUG, "Processing single type");  
                }            
                if (metadataType.equals(ANNOTATIONS_PROPERTY_NAME)) {
                    if (typeStr.equals(ANNOTATIONS_PROPERTY_NAME)) {
                        JsonElement annoE = sectionDSObject.get(ANNOTATIONS_PROPERTY_NAME);                   
                        if (annoE != null && annoE.isJsonArray()) {
                            dsResourceArray = annoE.getAsJsonArray();
                        }
                    }
                }
                if (metadataType.equals(LABELS_PROPERTY_NAME)) {
                    if (typeStr.equals(LABELS_PROPERTY_NAME)) {
                        JsonElement labelE = sectionDSObject.get(LABELS_PROPERTY_NAME);                   
                        if (labelE != null && labelE.isJsonArray()) {
                            dsResourceArray = labelE.getAsJsonArray();
                        }
                    }
                }
            }
                
            // find annotations or labels from resource metadata
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getMatchingResources", Logger.LogType.DEBUG, "Find annotations or labels from resource metadata");
            }
            if (dsResourceArray.size() > 0) {                                    
                JsonObject resPair = new JsonObject();
                for (JsonElement elem : dsResourceArray) {
                    if (elem != null && elem.isJsonPrimitive()) { 
                        String elemStr = elem.getAsString();
                        if (Logger.isDebugEnabled()) {
                            Logger.log(className, "getMatchingResources", Logger.LogType.DEBUG, "Processing elemStr=" + elemStr);
                        }
                        // find annotation or label from resource
                        JsonElement elemE = resObj.get(elemStr);
                        if (elemE != null && elemE.isJsonPrimitive()) { 
                            if (metadataType.equals(ANNOTATIONS_PROPERTY_NAME)) {
                                resPair.addProperty(ANNOTATION_PROPERTY_NAME, elemStr);     
                            } 
                            if (metadataType.equals(LABELS_PROPERTY_NAME)) {
                                resPair.addProperty(LABEL_PROPERTY_NAME, elemStr);  
                            }                         
                            resPair.addProperty(VALUE_PROPERTY_NAME, elemE.getAsString());                          
                        }
                    }
                }
                matchResources.add(resPair);
            }
               
            // get annotations or labels matches with prefixes 
            if (metadataType.equals(ANNOTATIONS_PROPERTY_NAME)) {
                dsResourcePrefixes = sectionDSObject.getAsJsonArray(ANNOTATION_PREFIXES_PROPERTY_NAME);
            }
            if (metadataType.equals(LABELS_PROPERTY_NAME)) {
                dsResourcePrefixes = sectionDSObject.getAsJsonArray(LABEL_PREFIXES_PROPERTY_NAME);
            }

            if (dsResourcePrefixes.size() > 0) { 
                for (JsonElement resprefix : dsResourcePrefixes) {
                    if (resprefix != null && resprefix.isJsonPrimitive()) { 
                        String resprefixStr = resprefix.getAsString();
                        if (Logger.isDebugEnabled()) {
                            Logger.log(className, "getMatchingResources", Logger.LogType.DEBUG, "Processing resprefixStr=" + resprefixStr);
                        }

                        // find matching annotations or labels from resource
                        resObj.entrySet().forEach(n -> {  
                            String key = n.getKey();
                            JsonElement value = n.getValue();
                            JsonObject res_key = new JsonObject();
                            if (Logger.isDebugEnabled()) {
                                Logger.log(className, "getMatchingResources", Logger.LogType.DEBUG, "Processing key=" + key);
                            }
                            
                            if (key != null) {
                                if (key.startsWith(resprefixStr)) {
                                    if (value != null && value.isJsonPrimitive()) {  
                                        if (metadataType.equals(ANNOTATIONS_PROPERTY_NAME)) {                                   
                                            res_key.addProperty(ANNOTATION_PROPERTY_NAME, key);
                                        }
                                        if (metadataType.equals(LABELS_PROPERTY_NAME)) {
                                            res_key.addProperty(LABEL_PROPERTY_NAME, key);
                                        }
                                        res_key.addProperty(VALUE_PROPERTY_NAME, value.getAsString());                                                                                                                                                                                                                                                                                                                                                                                                                                     
                                    }
                                } 
                            } 
                            
                            // filter out duplicate or empty array
                            if (res_key.entrySet().size() > 0 && ! matchResources.contains(res_key))
                                matchResources.add(res_key);     
                        });                      
                    }
                }              
            }
        }
                                   
        return matchResources;                                
    } 

}
