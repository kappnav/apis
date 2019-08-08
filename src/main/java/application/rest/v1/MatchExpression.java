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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A match expression of a selector in Kubernetes.
 */
public final class MatchExpression {
    
    private final String key;
    private final Operator operator;
    private final List<String> values;
    
    public MatchExpression(String key, Operator operator) {
        this(key, operator, 
                (operator == Operator.IN || operator == Operator.NOT_IN) ? 
                        new ArrayList<>() : Collections.emptyList());
    }
    
    private MatchExpression(String key, Operator operator, List<String> values) {
        this.key = key;
        this.operator = operator;
        this.values = values;
    }
    
    public MatchExpression addValue(String value) {
        values.add(value);
        return this;
    }
    
    public boolean matchesLabels(JsonObject labels) {
        JsonElement e = labels.get(key);
        switch (operator) {
        case IN:
            return matchesIn(e);
        case NOT_IN:
            return !matchesIn(e);
        case DOES_NOT_EXIST:
            return e == null;
        case EXISTS:
            return e != null;
        }
        return false;
    }
    
    private boolean matchesIn(JsonElement e) {
        if (e != null && e.isJsonPrimitive()) {
            String value = e.getAsString();
            return values.stream().anyMatch(v -> v.equals(value));
        }
        return false;
    }
    
    public void write(StringBuilder sb) {
        switch (operator) {
        case IN:
            sb.append(key);
            sb.append(" in ");
            writeValues(sb);
            break;
        case NOT_IN:
            sb.append(key);
            sb.append(" notin ");
            writeValues(sb);
            break;
        case DOES_NOT_EXIST:
            sb.append('!');
        case EXISTS:
            sb.append(key);
            break;
        }
    }
    
    private void writeValues(StringBuilder sb) {
        sb.append('(');
        final AtomicBoolean first = new AtomicBoolean(true);
        values.forEach(v -> {
            if (!first.get()) {
                sb.append(", ");
            }
            sb.append(v);
            first.set(false);
        });
        sb.append(')');
    }
    
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        write(sb);
        return sb.toString();
    }
      
    public enum Operator {
        IN,
        NOT_IN,
        EXISTS,
        DOES_NOT_EXIST;
        
        public static Operator enumValue(String value) {
            switch (value) {
            case "In":
                return IN;
            case "NotIn":
                return NOT_IN;
            case "Exists":
                return EXISTS;
            case "DoesNotExist":
                return DOES_NOT_EXIST;
            default:
                return null;     
            }
        }
    }
}
