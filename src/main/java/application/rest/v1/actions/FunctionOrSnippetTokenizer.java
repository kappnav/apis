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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Tokenizes a function or snippet string into its name and parameters.
 * e.g. add(${x},${y}) ---> { name: "add", parameters: [${x}, ${y}] }.
 */
public class FunctionOrSnippetTokenizer implements Iterable<String> {
    
    private String name;
    private final List<String> parameters;
    
    public FunctionOrSnippetTokenizer(String functionOrSnippet) {
        // Get the name of the function or snippet.
        final int nameEnd = functionOrSnippet.indexOf('(');
        if (nameEnd > 0 && functionOrSnippet.endsWith(")")) {
            name = functionOrSnippet.substring(0, nameEnd);
        }
        else {
            name = null;
            parameters = Collections.emptyList();
            return;
        }
        // Tokenize the parameters.
        parameters = new ArrayList<>();
        int mark = 0;
        int patternDepth = 0;
        // Capture the characters within '(' and ')'.
        final String v = functionOrSnippet.substring(nameEnd + 1, functionOrSnippet.length() - 1).trim();
        final int length = v.length();
        for (int i = 0; i < length; ++i) {
            final char c = v.charAt(i);
            // Search for parameter separator.
            if (c == ',') {
                if (patternDepth == 0) {
                    parameters.add(v.substring(mark, i).trim());
                    // Reached the end of the string.
                    if (i == length - 1) {
                        parameters.add("");
                    }
                    else {
                        mark = i + 1;
                    }
                }
                // Reached the end of the string.
                else if (i == length - 1) {
                    parameters.add(v.substring(mark, i + 1).trim());
                }
            }
            // Search for start of pattern segment: "${"
            else if (i < length - 1 && c == '$' && v.charAt(i + 1) == '{') {
                ++patternDepth;
            }
            // Search for end of pattern segment: "}"
            else if (c == '}' && patternDepth > 0) {
                --patternDepth;
                // Reached the end of the string.
                if (i == length - 1) {
                    parameters.add(v.substring(mark, i + 1).trim());
                }
            }
            // Reached the end of the string.
            else if (i == length - 1) {
                parameters.add(v.substring(mark, i + 1).trim());
            }
        }
    }

    @Override
    public Iterator<String> iterator() {
        return parameters.iterator();
    }
    
    public String getName() {
        return name;
    }
    
    public int getParameterCount() {
        return parameters.size();
    }
}
