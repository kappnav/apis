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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import application.rest.v1.MatchExpression.Operator;
import io.kubernetes.client.ApiClient;

/**
 * A selector in Kubernetes.
 */
public final class Selector {
    
    private static final String METADATA_PROPERTY_NAME = "metadata";
    private static final String LABELS_PROPERTY_NAME = "labels";
    
    private final Map<String,String> matchLabels = new LinkedHashMap<>();
    private final List<MatchExpression> matchExpressions = new ArrayList<>();
    
    public Selector() {}
    
    public Selector addMatchLabel(String key, String value) {
        matchLabels.put(key, value);
        return this;
    }
    
    public Selector addMatchExpression(MatchExpression expression) {
        matchExpressions.add(expression);
        return this;
    }
    
    public boolean isEmpty() {
        return matchLabels.isEmpty() && matchExpressions.isEmpty();
    }
    
    public boolean matches(ApiClient client, Object component) {
        return matches(client.getJSON().getGson().toJsonTree(component));
    }
    
    public boolean matches(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject root = element.getAsJsonObject();
            JsonElement metadata = root.get(METADATA_PROPERTY_NAME);
            if (metadata != null && metadata.isJsonObject()) {
                JsonObject metadataObj = metadata.getAsJsonObject();
                JsonElement labels = metadataObj.get(LABELS_PROPERTY_NAME);
                if (labels != null && labels.isJsonObject()) {
                    return matchesLabels(labels.getAsJsonObject());
                }
            }
        }
        return false;
    }
    
    public boolean matchesLabels(JsonObject labels) {
        final AtomicBoolean matches = new AtomicBoolean(false);
        if (!isEmpty()) {
            matches.set(true);
            // Check whether each of the match labels from the
            // selector matches the labels of the given label map.
            matchLabels.forEach((k,v) -> {
                if (matches.get()) {
                    final JsonElement e = labels.get(k);
                    if (e == null || !e.isJsonPrimitive() || !v.equals(e.getAsString())) {
                        matches.set(false);
                    }
                }
            });
            if (matches.get()) {
                // Check whether each of the match expressions from the
                // selector matches the labels of the given label map.
                matchExpressions.forEach(v -> {
                    if (matches.get() && !v.matchesLabels(labels)) {
                        matches.set(false);
                    }
                });
            }
        }
        return matches.get();
    }
    
    public void write(StringBuilder sb) {
        final AtomicBoolean first = new AtomicBoolean(true);
        matchLabels.forEach((k,v) -> {
            if (!first.get()) {
                sb.append(',');
            }
            sb.append(k);
            sb.append('=');
            sb.append(v);
            first.set(false);
        });
        matchExpressions.forEach(v -> {
            if (!first.get()) {
                sb.append(',');
            }
            v.write(sb);
            first.set(false);
        });
    }
    
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        write(sb);
        return sb.toString();
    }
    
    // Extract the selector from a component object.
    //
    // (Example)
    // selector:
    //  matchLabels:
    //   component: redis
    //  matchExpressions:
    //    - {key: tier, operator: In, values: [cache]}
    //    - {key: environment, operator: NotIn, values: [dev]}
    public static Selector getSelector(ApiClient client, Object component) {
        return getSelector(client.getJSON().getGson().toJsonTree(component));
    }
    
    public static Selector getSelector(final JsonElement element) {
        final Selector result = new Selector();
        if (element != null && element.isJsonObject()) {
            JsonObject root = element.getAsJsonObject();
            JsonElement spec = root.get("spec");
            if (spec != null && spec.isJsonObject()) {
                JsonObject specObj = spec.getAsJsonObject();
                JsonElement selector = specObj.get("selector");
                if (selector != null && selector.isJsonObject()) {
                    JsonObject selectorObj = selector.getAsJsonObject();
                    JsonElement matchLabels = selectorObj.get("matchLabels");
                    // Collect all of the {key, value} pairs from the matchLabels object.
                    if (matchLabels != null && matchLabels.isJsonObject()) {
                        JsonObject matchLabelsObj = matchLabels.getAsJsonObject();
                        matchLabelsObj.entrySet().forEach(v -> {  
                            JsonElement value = v.getValue();
                            if (value.isJsonPrimitive()) {
                                result.addMatchLabel(v.getKey(), value.getAsString());
                            }
                        });
                    }
                    // Collect all of the match expressions from the array under matchExpressions.
                    JsonElement matchExpressions = selectorObj.get("matchExpressions");
                    if (matchExpressions != null && matchExpressions.isJsonArray()) {
                        JsonArray matchExpArray = matchExpressions.getAsJsonArray();
                        matchExpArray.forEach(v -> {
                            // Process each match expression: {key: ..., operator: ..., values: [...]}.
                            if (v != null && v.isJsonObject()) {
                                JsonObject matchExpression = v.getAsJsonObject();
                                JsonElement key = matchExpression.get("key");
                                JsonElement operator = matchExpression.get("operator");
                                if (key != null && operator != null) {
                                    final Operator op = Operator.enumValue(operator.getAsString());
                                    if (op != null) {
                                        final MatchExpression me = new MatchExpression(key.getAsString(), op);
                                        if (op == Operator.EXISTS || op == Operator.DOES_NOT_EXIST) {
                                            result.addMatchExpression(me);
                                        }
                                        else {
                                            JsonElement values = matchExpression.get("values");
                                            if (values != null && values.isJsonArray()) {
                                                JsonArray valuesArray = values.getAsJsonArray();
                                                if (valuesArray.size() > 0) {
                                                    valuesArray.forEach(w -> {
                                                        if (w != null && w.isJsonPrimitive()) {
                                                            me.addValue(w.getAsString());
                                                        }
                                                    });
                                                    result.addMatchExpression(me);
                                                }
                                            }   
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }
        return result;
    }
}
