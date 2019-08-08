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

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import application.rest.v1.configmaps.StatusMappingConfigMapBuilder;
import application.rest.v1.json.JSONPath;
import application.rest.v1.json.JSONPathParser;
import io.kubernetes.client.ApiClient;

public class StatusProcessor {
    
    private static final String STATUS_PROPERTY_NAME = "status";
    
    private static final String EXPRESSION_PROPERTY_NAME = "expression";
    private static final String MATCHES_PROPERTY_NAME = "matches";
    private static final String ELSE_PROPERTY_NAME = "else";
    
    private final KAppNavConfig config;

    public StatusProcessor(KAppNavConfig config) {
        this.config = config;
    }
    
    public JsonObject getComponentStatus(ApiClient client, ComponentInfoRegistry registry, JsonObject component, JsonObject configMap) {
        JsonObject statusObject = null;
        
        final JsonElement componentStatusElement = component.get(STATUS_PROPERTY_NAME);
        final JsonObject componentStatusObj = (componentStatusElement != null && componentStatusElement.isJsonObject()) ? componentStatusElement.getAsJsonObject() : null;

        if (configMap != null) {
            // exists
            JsonElement exists = configMap.get(StatusMappingConfigMapBuilder.EXISTS_PROPERTY_NAME);
            if (exists != null) {
                if (exists.isJsonObject()) {
                    statusObject = exists.getAsJsonObject();
                }
            }
            else {
                // jsonpath
                JsonElement jsonpath = configMap.get(StatusMappingConfigMapBuilder.JSONPATH_PROPERTY_NAME);
                if (jsonpath != null) {
                    if (jsonpath.isJsonObject()) {
                        final JsonObject jsonpathObj = jsonpath.getAsJsonObject();
                        
                        final JsonElement expressionElement = jsonpathObj.get(EXPRESSION_PROPERTY_NAME);
                        if (expressionElement != null && expressionElement.isJsonPrimitive()) {
                            String expression = expressionElement.getAsString();
                            if (!expression.startsWith("$")) {
                                expression = "$" + expression;
                            }
                            // Parse the JSON path expression and attempt to match it against the status object.
                            final JSONPathParser parser = new JSONPathParser();
                            final JSONPath path = parser.parse(expression);
                            if (path != null) {
                                final String statusValue = path.resolveLeaf(componentStatusObj);
                                if (statusValue != null) {
                                    JsonObject matchedStatusObj = null;
                                    // Find a mapping in the 'matches' map.
                                    final JsonElement matchesElement = jsonpathObj.get(MATCHES_PROPERTY_NAME);
                                    if (matchesElement != null && matchesElement.isJsonObject()) {
                                        final JsonObject matchesObj = matchesElement.getAsJsonObject();
                                        final JsonElement matchedValue = matchesObj.get(statusValue);
                                        if (matchedValue != null && matchedValue.isJsonObject()) {
                                            matchedStatusObj = matchedValue.getAsJsonObject();
                                        }
                                    }
                                    if (matchedStatusObj == null) {
                                        // Did not match any of the named mappings. Use the 'else' status mapping if it exists.
                                        final JsonElement elseMappingElement = jsonpathObj.get(ELSE_PROPERTY_NAME);
                                        if (elseMappingElement != null && elseMappingElement.isJsonObject()) {
                                            matchedStatusObj = elseMappingElement.getAsJsonObject();
                                        }
                                    }
                                    if (matchedStatusObj != null) {
                                        statusObject = KAppNavEndpoint.resolveStatusOperator(matchedStatusObj, statusValue);
                                    }
                                }
                            }
                        }
                    }
                } 
                else {
                    // algorithm
                    JsonElement algorithm = configMap.get(StatusMappingConfigMapBuilder.ALGORITHM_PROPERTY_NAME);
                    if (algorithm != null) {
                        // Invoke the snippet using the built-in JavaScript engine.
                        ScriptEngineManager sce = new ScriptEngineManager();
                        ScriptEngine se = sce.getEngineByName("JavaScript");
                        try {
                            se.eval(algorithm.getAsString());
                            Invocable inv = (Invocable) se; // This cast should never fail.
                            String status = (componentStatusObj != null) ? componentStatusObj.toString() : "";
                            Object o = inv.invokeFunction("getStatus", status);
                            if (o != null) {
                                JsonParser parser = new JsonParser();
                                JsonElement element = parser.parse(o.toString());
                                if (element.isJsonObject()) {
                                    statusObject = element.getAsJsonObject();
                                }
                            }
                        }
                        // If the invocation of the snippet failed or the JSON it returned
                        // was malformed, fall through and return "Unknown" as the status.
                        catch (ClassCastException | JsonSyntaxException | NoSuchMethodException | SecurityException | ScriptException e) {}
                    }
                }
            }
        }
        // If the status object is null or contains an unrecognized value then return "Unknown" as the status.
        if (statusObject != null && KAppNavEndpoint.hasKnownStatus(statusObject, config)) {
            return statusObject;
        }
        return KAppNavEndpoint.createUnknownStatusObject(config);
    }
}
