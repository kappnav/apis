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

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.ibm.kappnav.logging.Logger;

public final class SectionConfigMapBuilder extends ConfigMapBuilder {

    private static final String className = SectionConfigMapBuilder.class.getName();

    private static final String SECTIONS_PROPERTY_NAME = "sections";
    private static final String SECTION_DATASOURCES_PROPERTY_NAME = "section-datasources";
    private static final String NAME_PROPERTY_NAME = "name";
         
    private final Set<String> sectionNames = new HashSet<>();
    private final Set<String> sectionDataSourcesNames = new HashSet<>();
   

    public SectionConfigMapBuilder() {
        super();
    }

    public JsonObject getConfigMap() {
        return map;
    }

    // Merges the data sections of section config maps into a unified map
    // of sections and section-datasources in the following format :
    //
    // {
    //   sections: []
    //   section-datasources: []
    // }
    //
    // This method detects and ignores duplicate entries in the data to be merged.
    public void merge(final JsonObject otherMap) {
        final JsonElement data = otherMap.get(DATA_PROPERTY_NAME);
        if (data != null && data.isJsonObject()) {
            final JsonObject dataObject = data.getAsJsonObject();
            mergeSections(dataObject, SECTIONS_PROPERTY_NAME, sectionNames);
            mergeSections(dataObject, SECTION_DATASOURCES_PROPERTY_NAME, sectionDataSourcesNames);                      
        }
    }

    private void mergeSections(final JsonObject otherData, final String sectionGroup, final Set<String> usedNames) {
        JsonElement ag = otherData.get(sectionGroup);
        if (ag != null) {
            if (ag.isJsonPrimitive()) {
                // Expand and replace the string value with an object.
                final JsonParser parser = new JsonParser();
                try {
                    ag = parser.parse(ag.getAsString());
                    otherData.add(sectionGroup, ag);
                }
                catch (JsonSyntaxException e) {
                    Logger.log(className, "mergeSections", Logger.LogType.DEBUG, "Caught JsonSyntaxException " + e.toString());
                }
            }
            if (ag.isJsonArray()) {
                final JsonArray agObject = ag.getAsJsonArray();
                if (agObject.size() > 0) {
                    JsonElement mergedGroup = map.get(sectionGroup);
                    if (mergedGroup == null) {
                        mergedGroup = new JsonArray();
                        map.add(sectionGroup, mergedGroup);
                    }
                    final JsonArray groupArray = mergedGroup.getAsJsonArray();
                    agObject.forEach(v -> {
                        if (v != null && v.isJsonObject()) {
                            final JsonObject sectionObject = v.getAsJsonObject();
                            final JsonElement name = sectionObject.get(NAME_PROPERTY_NAME);
                            if (name != null && name.isJsonPrimitive()) {
                                final String nameStr = name.getAsString();
                                if (!usedNames.contains(nameStr)) {
                                    groupArray.add(sectionObject);
                                    usedNames.add(nameStr);
                                }  
                            }
                        }
                    });
                }
            }
        } else {
            Logger.log(className, "mergeSections", Logger.LogType.DEBUG, "Section group is null.");
        }
    }    
}
