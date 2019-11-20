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

package application.rest.v1.actions;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.System;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import application.rest.v1.ComponentInfoRegistry;
import application.rest.v1.KAppNavConfig;
import application.rest.v1.configmaps.ConfigMapProcessor;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;

public final class ResolutionContext {
    
    private static final String JAVA_SCRIPT_FUNCTION_PREFIX = "function ";
    
    private static final String GLOBAL_NAMESPACE = KAppNavConfig.getkAppNavNamespace();
    
    private static final String URL_ACTIONS_PROPERTY_NAME = "url-actions";
    private static final String CMD_ACTIONS_PROPERTY_NAME = "cmd-actions";
    private static final String FUNCTION_ACTIONS_PROPERTY_NAME = "function-actions";
    private static final String INPUTS_PROPERTY_NAME = "inputs";
    private static final String SNIPPETS_PROPERTY_NAME = "snippets";
    private static final String VARIABLES_PROPERTY_NAME = "variables";
    
    private static final String NAME_PROPERTY_NAME = "name";
    private static final String FIELDS_PROPERTY_NAME = "fields";
    private static final String DEFAULT_PROPERTY_NAME = "default";
    private static final String OPTIONAL_PROPERTY_NAME = "optional";
    private static final String VALIDATOR_PROPERTY_NAME = "validator";
    
    private static final String VALUE_PROPERTY_NAME = "value";
    private static final String VALID_PROPERTY_NAME = "valid";
    private static final String MESSAGE_PROPERTY_NAME = "message";
    
    private static final Map<String,Resolver> resolvers;
    static {
        resolvers = new HashMap<>();
        addResolver(new ResourceResolver());
        addResolver(new BuiltinResolver());
        addResolver(new GlobalResolver());
        addResolver(new FunctionResolver());
        addResolver(new InputResolver());
        addResolver(new SnippetResolver());
        addResolver(new VariableResolver());
    }
    
    private static void addResolver(Resolver resolver) {
        System.out.println("JUNIRC.. addResolver "+ resolver.toString());
        final String name = resolver.getName();
        if (!resolvers.containsKey(name)) {
            resolvers.put(name, resolver);
        }
    }
    
    private final ApiClient client;
    private final ComponentInfoRegistry registry;
    private final JsonObject resource;
    private JsonObject resourceMap;
    private final String resourceKind;
    private JsonObject userInputMap;
    private final Map<String,V1ConfigMap> kappnavNSMapCache;
    private final Map<String,String> resolvedVariables;
    private final Deque<String> visitedVariables;
    
    public ResolutionContext(ApiClient client, ComponentInfoRegistry registry, JsonObject resource, String resourceKind) {
        this.client = client;
        this.registry = registry;
        this.resource = resource;
        this.resourceKind = resourceKind;
        this.userInputMap = new JsonObject();
        this.kappnavNSMapCache = new HashMap<>();
        this.resolvedVariables = new HashMap<>();
        this.visitedVariables = new ArrayDeque<>();
    }
    
    public ApiClient getApiClient() {
        return client;
    }
    
    public ComponentInfoRegistry getComponentInfoRegistry() {
        return registry;
    }
    
    public JsonObject getResource() {
        System.out.println("JUNIRC.. getResource " + resource.toString());
        return resource;
    }
    
    public String getResourceKind() {
        System.out.println("JUNIRC.. getResource " + resourceKind.toString());
        return resourceKind;
    }
    
    // This method validates the user's input before setting it on the ResolutionContext.
    public void setUserInput(JsonObject userInputMap, JsonObject fields) throws ValidationException {
        System.out.println("JUNIRC.. setUserInput userInputMap=" + userInputMap.toString() + " fields=" + fields.toString());
        JsonObject map = (userInputMap != null) ? userInputMap : new JsonObject();
        // Validate each of the fields specified by the user.
        map.entrySet().forEach(entry -> {
            final String key = entry.getKey();
            if (!fields.has(key)) {
                // REVISIT: Message translation required.
                throw new ValidationException("The field was specified but was not expected.", key);
            }
            final JsonElement element = entry.getValue();
            if (element == null || !element.isJsonPrimitive()) {
                // REVISIT: Message translation required.
                throw new ValidationException("The field was specified but does not have a primitive value.", key);
            }
            final JsonElement fieldElement = fields.get(key);
            if (fieldElement != null && fieldElement.isJsonObject()) {
                final JsonObject field = fieldElement.getAsJsonObject();
                final String validatorSnippet = getFieldValidatorSnippet(field);
                // Invoke the JavaScript snippet to validate the specified value.
                if (validatorSnippet != null) {
                    validateField(key, field, element.getAsString(), validatorSnippet);
                }
            }
        });
        // Apply default values. Report an error for a required field that is missing.
        fields.entrySet().forEach(entry -> {
            final String key = entry.getKey();
            if (!map.has(key)) {
                final JsonElement element = entry.getValue();
                if (element != null && element.isJsonObject()) {
                    final JsonObject field = element.getAsJsonObject();
                    if (isFieldOptional(field)) {
                        final String defaultValue = getFieldDefaultValue(field);
                        if (defaultValue != null) {
                            map.addProperty(key, defaultValue);
                        }
                    }
                    else {
                        // REVISIT: Message translation required.
                        throw new ValidationException("A required field was not specified.", key);
                    }
                }
            }
        });
        this.userInputMap = map;
    }
    
    // This method validates the value of a user supplied input against a JavaScript snippet.
    private void validateField(String name, JsonObject field, String value, String snippet) throws ValidationException {
        System.out.println("JUNIRC.. validateField name=" + name + " fields=" +field.toString() + " value=" + value + " snippet=" +snippet);
        // snippet input: json containing input field value in form: { "value": "<field-value>" }
        final JsonObject input = new JsonObject();
        input.addProperty(VALUE_PROPERTY_NAME, value);
        final String inputAsString = input.toString();
        
        // Invoke the validator.
        final String outputAsString = invokeSnippet(snippet, Collections.singletonList(inputAsString));
        if (outputAsString == null) {
            // REVISIT: Message translation required.
            throw new ValidationException("Validation of the field failed due to an internal error.", name);
        }
        
        // snippet output: { "valid": true | false, "message": "<error-message>"}
        try {
            final JsonParser parser = new JsonParser();
            final JsonElement element = parser.parse(outputAsString);
            if (element.isJsonObject()) {
                final JsonObject output = element.getAsJsonObject();
                // If the field is valid return normally.
                if (Boolean.parseBoolean(getPrimitivePropertyValue(output, VALID_PROPERTY_NAME))) {
                    return;
                }
                final String message = getPrimitivePropertyValue(output, MESSAGE_PROPERTY_NAME);
                if (message != null) {
                    // REVISIT: Message translation required.
                    throw new ValidationException("The field is invalid. Reason: " + message, name);
                }
            }
            // REVISIT: Message translation required.
            throw new ValidationException("The validation result for the field could not be processed because it was in the wrong format.", name);
        }
        catch (JsonSyntaxException e) {
            System.out.println("JUNI.. caught JsonSyntaxException " + e.toString());
            // REVISIT: Message translation required.
            throw new ValidationException("The validation result for the field could not be processed. Reason: " + e.getMessage(), name);
        }
    }
    
    public String getUserInputValue(String fieldName) {
        System.out.println("JUNIRC.. getUserInputValue fieldName=" + fieldName);
        return getPrimitivePropertyValue(userInputMap, fieldName);
    }
    
    public JsonObject getURLAction(String name) {
        System.out.println("JUNIRC.. getURLAction name=" + name);
        return getAction(name, URL_ACTIONS_PROPERTY_NAME);
    }
    
    public JsonObject getCommandAction(String name) {
        System.out.println("JUNIRC.. getCommandAction name="+ name);
        return getAction(name, CMD_ACTIONS_PROPERTY_NAME);
    }
    
    public JsonObject getFunctionAction(String name) {
        System.out.println("JUNIRC.. getFunctionAction name="+ name);
        return getAction(name, FUNCTION_ACTIONS_PROPERTY_NAME);
    }
    
    private JsonObject getAction(String name, String type) {
        System.out.println("JUNIRC.. getAction name="+ name + " type="+type);
        initializeResourceMap();
        final JsonElement e = resourceMap.get(type);
        if (e != null && e.isJsonArray()) {
            final JsonArray array = e.getAsJsonArray();
            for (JsonElement arrayElement : array) {
                if (arrayElement != null && arrayElement.isJsonObject()) {
                    final JsonObject actionObject = arrayElement.getAsJsonObject();
                    final JsonElement actionName = actionObject.get(NAME_PROPERTY_NAME);
                    if (actionName != null && actionName.isJsonPrimitive()) {
                        final String nameStr = actionName.getAsString();
                        if (name.equals(nameStr)) {
                            return actionObject;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    public JsonObject getInputFields(String inputName) {
        System.out.println("JUNIRC.. getInputFields inputName=" + inputName);
        initializeResourceMap();
        JsonElement e = resourceMap.get(INPUTS_PROPERTY_NAME);
        if (e != null && e.isJsonObject()) {
            JsonObject map = e.getAsJsonObject();
            e = map.get(inputName);
            if (e != null && e.isJsonObject()) {
                map = e.getAsJsonObject();
                e = map.get(FIELDS_PROPERTY_NAME);
                if (e != null && e.isJsonObject()) {
                    return e.getAsJsonObject();
                }
            }
        }
        return null;
    }
    
    public JsonObject getInputField(String inputName, String fieldName) {
        System.out.println("JUNIRC.. getInputField inputName=" + inputName + " fieldName=" + fieldName);
        return getInputField(getInputFields(inputName), fieldName);
    }
    
    public JsonObject getInputField(JsonObject fields, String fieldName) {
        System.out.println("JUNIRC.. getInputField fields=" + fields.toString() + " fieldName=" + fieldName);
        if (fields != null) {
            JsonElement e = fields.get(fieldName);
            if (e != null && e.isJsonObject()) {
                return e.getAsJsonObject();
            }
        }
        return null;
    }
    
    public String getFieldDefaultValue(JsonObject field) {
        System.out.println("JUNIRC.. getFieldDefaultValue " + field.toString());
        return getPrimitivePropertyValue(field, DEFAULT_PROPERTY_NAME);
    }
    
    public boolean isFieldOptional(JsonObject field) {
        System.out.println("JUNIRC.. isFieldOptional " + field.toString());
        return Boolean.parseBoolean(getPrimitivePropertyValue(field, OPTIONAL_PROPERTY_NAME));
    }
    
    public String getFieldValidatorSnippet(JsonObject field) {
        System.out.println("JUNIRC.. getFieldValidatorSnippet " + field.toString());
        final String snippetName = getPrimitivePropertyValue(field, VALIDATOR_PROPERTY_NAME);
        if (snippetName != null) {
            return getSnippet(snippetName);
        }
        return null;
    }
    
    private static String getPrimitivePropertyValue(JsonObject map, String propName) {
        System.out.println("JUNIRC.. getPrimitivePropertyValue map=" + map.toString() + " propName=" + propName);
        if (map != null) {
            JsonElement e = map.get(propName);
            if (e != null && e.isJsonPrimitive()) {
                return e.getAsString();
            }
        }
        return null;
    }
    
    public String getVariablePattern(String name) {
        System.out.println("JUNIRC.. getVariablePattern " + name);
        return getMapValue(name, VARIABLES_PROPERTY_NAME);
    }
    
    public String getSnippet(String name) {
        System.out.println("JUNIRC.. getSnippet " + name);
        return getMapValue(name, SNIPPETS_PROPERTY_NAME);
    }
    
    private String getMapValue(String name, String type) {
        System.out.println("JUNIRC.. getMapValue name=" + name + " type=" + type);
        initializeResourceMap();
        JsonElement e = resourceMap.get(type);
        if (e != null && e.isJsonObject()) {
            JsonObject map = e.getAsJsonObject();
            e = map.get(name);
            if (e != null && e.isJsonPrimitive()) {
                return e.getAsString();
            }
        }
        return null;
    }
    
    public String getResolvedVariable(String name) {
        System.out.println("JUNIRC.. getResolvedVariable " + name);
        return resolvedVariables.get(name);
    }
    
    public void setResolvedVariable(String name, String value) {
        System.out.println("JUNIRC.. setResolvedVariable name=" + name + " value=" + value);
        resolvedVariables.put(name, value);
    }
    
    public void visitVariableStart(String name) {
        System.out.println("JUNIRC.. visitVariableStart " + name);
        visitedVariables.push(name);
    }
    
    public void visitVariableEnd() {
        System.out.println("JUNIRC.. visitVariableEnd");
        if (visitedVariables.size() > 0) {
            visitedVariables.pop();
        }
    }
    
    public boolean isVisitingVariable(String name) {
        System.out.println("JUNIRC.. isVisitingVariable " + name);
        return visitedVariables.contains(name);
    }
    
    private void initializeResourceMap() {
        System.out.println("JUNIRC.. initializeResourceMap");
        if (resourceMap == null) {
            ConfigMapProcessor processor = new ConfigMapProcessor(resourceKind);
            resourceMap = processor.getConfigMap(client, resource, ConfigMapProcessor.ConfigMapType.ACTION);
        }
    }
    
    public String getConfigMapDataField(String mapName, String mapField) {
        System.out.println("JUNIRC.. getConfigMapDataField mapName=" + mapName + " mapField=" + mapField);
        if (kappnavNSMapCache.containsKey(mapName)) {
            // Return value from the local cache.
            final V1ConfigMap map = kappnavNSMapCache.get(mapName);
            if (map != null) {
                return map.getData().get(mapField);
            }
            return null;
        }
        try {
            CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);
            final V1ConfigMap map = api.readNamespacedConfigMap(mapName, GLOBAL_NAMESPACE, null, null, null);
            Map<String,String> data = map.getData();
            if (data != null) {
                // Store the map in the local cache.
                kappnavNSMapCache.put(mapName, map);
                return data.get(mapField);
            }
        }
        catch (ApiException e) {
            System.out.println("JUNI.. caught ApiException" + e.toString());
        }
        // No map or no data section. Store null in the local cache.
        kappnavNSMapCache.put(mapName, null);
        return null;
    }
    
    public long getTimeout() {
        return Command.DEFAULT_TIMEOUT;
    }
    
    public TimeUnit getTimeoutUnit() {
        return Command.DEFAULT_TIMEOUT_UNIT;
    }
    
    // snippet :: "function xyz(a,b,c) {...}"
    public String invokeSnippet(String snippet, List<String> parameters) throws ValidationException {
        System.out.println("JUNIRC.. invokeSnippet snippet=" + snippet + " parameters=" + parameters.toString());
        // Determine the function name by inspecting the snippet.
        String functionName = null;
        int start = snippet.indexOf(JAVA_SCRIPT_FUNCTION_PREFIX);
        if (start != -1) {
            int end = snippet.indexOf('(', start);
            if (end != -1) {
                functionName = snippet.substring(start + JAVA_SCRIPT_FUNCTION_PREFIX.length(), end).trim();
            }
        }
        // No function found in the snippet.
        if (functionName == null) {
            return null;
        }
        
        // Invoke the snippet using the built-in JavaScript engine.
        ScriptEngineManager sce = new ScriptEngineManager();
        ScriptEngine se = sce.getEngineByName("JavaScript");
        try {
            se.eval(snippet);
            Invocable inv = (Invocable) se; // This cast should never fail.
            Object o = inv.invokeFunction(functionName, (Object[]) parameters.toArray());
            if (o != null) {
                return o.toString();
            }
        }
        catch (ClassCastException | NoSuchMethodException | SecurityException | ScriptException e) {
            System.out.println("JUNIRC.. caught many exception retrow now" + e.toString());
            throw new ValidationException("Problem invoking snippet, Reason=" + e.toString());
        }
        return null;
    }
    
    public ResolvedValue resolve(String pattern) throws ValidationException {
        System.out.println("JUNIRC.. resolve " + pattern);
        final StringBuilder result = new StringBuilder();
        final PatternTokenizer tokenizer = new PatternTokenizer(pattern);
        final AtomicBoolean isFullyResolved = new AtomicBoolean(true);
        try {
            tokenizer.forEach(t -> {
            // Add string literals directly to the result.
            if (!t.isPattern()) {
                result.append(t.getDecodedValue());
            }
            // 1) Select a resolver that matches the prefix of the token.
            // 2) Invoke the resolver.
            // 3) Append the return value to the result.
            else {
                final String value = t.getValue();
                final int i = value.indexOf('.');
                if (i >= 0) {
                    final String prefix = value.substring(0, i);
                    final Resolver resolver = resolvers.get(prefix);
                    if (resolver != null) {
                        final String suffix = value.substring(i + 1);
                        String s = resolver.resolve(this, suffix);
                        if (s != null) {
                            result.append(s);
                        }
                        else {
                            isFullyResolved.set(false);
                            result.append(t.toString());
                        }
                    }
                    else {
                        isFullyResolved.set(false);
                        result.append(t.toString());
                    }
                }
                else {
                    isFullyResolved.set(false);
                    result.append(t.toString());
                }
            }
        });
    } catch (ValidationException e) {
        System.out.println("JUNIRC.. caught ValidationException " + e.toString());
        throw e;
    }
        return new ResolvedValue(result.toString(), isFullyResolved.get());
    }
    
    public static class ResolvedValue {
        private final String value;
        private final boolean isFullyResolved;
        public ResolvedValue(String value, boolean isFullyResolved) {
            System.out.println("JUNIRC.. ResolvedValue value=" + value + " isFullyResolved=" + isFullyResolved);
            this.value = value;
            this.isFullyResolved = isFullyResolved;
        }
        public String getValue() {
            System.out.println("JUNIRC.. getValue");
            return value;
        }
        public boolean isFullyResolved() {
            System.out.println("JUNIRC.. isFullyResolved");
            return isFullyResolved;
        }
    }
}
