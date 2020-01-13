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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.ibm.kappnav.logging.Logger;

public final class StatusMappingConfigMapBuilder extends ConfigMapBuilder {

    public static final String EXISTS_PROPERTY_NAME = "exists";
    public static final String JSONPATH_PROPERTY_NAME = "jsonpath";
    public static final String ALGORITHM_PROPERTY_NAME = "algorithm";

    public StatusMappingConfigMapBuilder() {
        super();
    }

    public JsonObject getConfigMap() {
        return map;
    }

    // Merges the data sections of status mapping config maps into a unified map
    // in the following format :
    //
    // {
    //   exists: {}
    //   jsonpath: {}
    //   algorithm: ""
    // }
    // We don't need to verify unique names because each map will only have 1 status mapping
    public void merge(final JsonObject otherMap) {
        final JsonElement data = otherMap.get(DATA_PROPERTY_NAME);
        if (data != null && data.isJsonObject()) {
            final JsonObject dataObject = data.getAsJsonObject();
            merge(dataObject, EXISTS_PROPERTY_NAME);
            merge(dataObject, JSONPATH_PROPERTY_NAME);
            mergeAlgorithm(dataObject, ALGORITHM_PROPERTY_NAME);
        }
    }

    private void merge(final JsonObject otherData, final String group) {
        JsonElement value = otherData.get(group);
        if (value != null) {
            if (value.isJsonPrimitive()) {
                final JsonParser parser = new JsonParser();
                try {
                    value = parser.parse(value.getAsString());
                    otherData.add(group, value);
                    map.add(group, value);
                }
                catch (JsonSyntaxException e) {
                    Logger.log(StatusMappingConfigMapBuilder.class.getName(), "merge", Logger.LogType.DEBUG, "Caught JsonSyntaxException " + e.toString());
                }
            } 
            else if(value.isJsonObject()) {
                map.add(group, value);
            }
        }
    }

    private void mergeAlgorithm(final JsonObject otherData, final String group) {
        JsonElement value = otherData.get(group);
        if (value != null) {
            map.add(group, value);
        }
    }
}
