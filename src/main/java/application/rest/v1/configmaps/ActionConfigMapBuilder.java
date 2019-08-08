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

public final class ActionConfigMapBuilder extends ConfigMapBuilder {

    private static final String URL_ACTIONS_PROPERTY_NAME = "url-actions";
    private static final String CMD_ACTIONS_PROPERTY_NAME = "cmd-actions";
    private static final String FUNCTION_ACTIONS_PROPERTY_NAME = "function-actions";
    private static final String INPUTS_PROPERTY_NAME = "inputs";
    private static final String SNIPPETS_PROPERTY_NAME = "snippets";
    private static final String VARIABLES_PROPERTY_NAME = "variables"; 

    private static final String NAME_PROPERTY_NAME = "name";

    private final Set<String> urlActionNames = new HashSet<>();
    private final Set<String> cmdActionNames = new HashSet<>();
    private final Set<String> functionActionNames = new HashSet<>();

    public ActionConfigMapBuilder() {
        super();
    }

    public JsonObject getConfigMap() {
        return map;
    }

    // Merges the data sections of action config maps into a unified map
    // of actions and snippets in the following format :
    //
    // {
    //   url-actions: []
    //   cmd-actions: []
    //   function-actions: []
    //   inputs: {}
    //   snippets: {}
    //   variables: {}
    // }
    //
    // This method detects and ignores duplicate entries in the data to be merged.
    public void merge(final JsonObject otherMap) {
        final JsonElement data = otherMap.get(DATA_PROPERTY_NAME);
        if (data != null && data.isJsonObject()) {
            final JsonObject dataObject = data.getAsJsonObject();
            mergeActions(dataObject, URL_ACTIONS_PROPERTY_NAME, urlActionNames);
            mergeActions(dataObject, CMD_ACTIONS_PROPERTY_NAME, cmdActionNames);
            mergeActions(dataObject, FUNCTION_ACTIONS_PROPERTY_NAME, functionActionNames);
            mergeMaps(dataObject, INPUTS_PROPERTY_NAME);
            mergeMaps(dataObject, SNIPPETS_PROPERTY_NAME);
            mergeMaps(dataObject, VARIABLES_PROPERTY_NAME);
        }
    }

    private void mergeActions(final JsonObject otherData, final String actionGroup, final Set<String> usedNames) {
        JsonElement ag = otherData.get(actionGroup);
        if (ag != null) {
            if (ag.isJsonPrimitive()) {
                // Expand and replace the string value with an object.
                final JsonParser parser = new JsonParser();
                try {
                    ag = parser.parse(ag.getAsString());
                    otherData.add(actionGroup, ag);
                }
                catch (JsonSyntaxException e) {}
            }
            if (ag.isJsonArray()) {
                final JsonArray agObject = ag.getAsJsonArray();
                if (agObject.size() > 0) {
                    JsonElement mergedGroup = map.get(actionGroup);
                    if (mergedGroup == null) {
                        mergedGroup = new JsonArray();
                        map.add(actionGroup, mergedGroup);
                    }
                    final JsonArray groupArray = mergedGroup.getAsJsonArray();
                    agObject.forEach(v -> {
                        if (v != null && v.isJsonObject()) {
                            final JsonObject actionObject = v.getAsJsonObject();
                            final JsonElement name = actionObject.get(NAME_PROPERTY_NAME);
                            if (name != null && name.isJsonPrimitive()) {
                                final String nameStr = name.getAsString();
                                if (!usedNames.contains(nameStr)) {
                                    groupArray.add(actionObject);
                                    usedNames.add(nameStr);
                                }  
                            }
                        }
                    });
                }
            }
        }
    }

    private void mergeMaps(final JsonObject otherData, final String mapName) {
        JsonElement otherMap = otherData.get(mapName);
        if (otherMap != null) {
            if (otherMap.isJsonPrimitive()) {
                // Expand and replace the string value with an object.
                final JsonParser parser = new JsonParser();
                try {
                    otherMap = parser.parse(otherMap.getAsString());
                    otherData.add(mapName, otherMap);
                }
                catch (JsonSyntaxException e) {}
            }
            if (otherMap.isJsonObject()) {
                final JsonObject otherMapObj = otherMap.getAsJsonObject();
                final Set<Entry<String, JsonElement>> otherEntrySet = otherMapObj.entrySet();
                if (!otherEntrySet.isEmpty()) {
                    JsonElement thisMap = map.get(mapName);
                    if (thisMap == null) {
                        thisMap = new JsonObject();
                        map.add(mapName, thisMap);
                    }
                    final JsonObject thisMapObj = thisMap.getAsJsonObject();
                    otherEntrySet.forEach(v -> {
                        String name = v.getKey();
                        if (thisMapObj.get(name) == null) {
                            thisMapObj.add(name, v.getValue());
                        }
                    });
                }
            }
        }  
    }
}
