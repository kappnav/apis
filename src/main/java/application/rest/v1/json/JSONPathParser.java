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

package application.rest.v1.json;

import java.util.ArrayList;
import java.util.List;

// Parses a subset of JSON Path (only supports child-axis; dot and bracket notation).
public final class JSONPathParser {
    
    /**
     * Supports simple JSON path expressions (e.g. $.one.two.three, $['one']['two']['three'])
     * Returns a JSONPath object for a valid expression otherwise null if the expression is
     * invalid or not supported.
     */
    public JSONPath parse(String path) {
        List<String> children = new ArrayList<>();
        int mark = 0;
        ParserState state = ParserState.START_EXPRESSION;
        final int length = path.length();
        for (int i = 0; i < length; ++i) {
            char c = path.charAt(i);
            // State machine for walking the JSON path expression.
            switch (state) {
                case START_EXPRESSION:
                    if (path.charAt(0) == '$') {
                        state = ParserState.START_CHILD;
                    }
                    else {
                        return null;
                    }
                    break;
                case START_CHILD:
                    if (i < length - 1) {
                        if (c == '.') {
                            state = ParserState.CHILD_NAME_DOT_NOTATION;
                            mark = i + 1;
                        }
                        else if (c == '[') {
                            state = ParserState.START_CHILD_BRACKET_NOTATION;
                        }
                        else {
                            return null;
                        }
                    }
                    else {
                        return null;
                    }
                    break;
                case CHILD_NAME_DOT_NOTATION:
                    if (c == '.' || c == '[') {
                        if (i < length - 1) {
                            if (i > mark) {
                                // Reached end of child name. Add it to the list.
                                children.add(path.substring(mark, i));
                            }
                            else {
                                return null;
                            }
                            if (c == '.') {
                                mark = i + 1;
                            }
                            else { // '['
                                state = ParserState.START_CHILD_BRACKET_NOTATION;
                            }
                        }
                        else {
                            return null;
                        }
                    }
                    else if (i == length - 1) {
                        // Reached end of child name. Add it to the list.
                        children.add(path.substring(mark, i + 1));
                    }
                    break;
                case START_CHILD_BRACKET_NOTATION:
                    if (i < length - 1) {
                        if (c == '\'') {
                            state = ParserState.CHILD_NAME_BRACKET_NOTATION;
                            mark = i + 1;
                        }
                        else {
                            return null;
                        }
                    }
                    else {
                        return null;
                    }
                    break;
                case CHILD_NAME_BRACKET_NOTATION:
                    if (i < length - 1) {
                        if (c == '\'') {
                            if (i > mark) {
                                // Reached end of child name. Add it to the list.
                                children.add(path.substring(mark, i));
                            }
                            else {
                                return null;
                            }
                            state = ParserState.END_CHILD_BRACKET_NOTATION;
                        }
                    }
                    else {
                        return null;
                    }
                    break;
                case END_CHILD_BRACKET_NOTATION:
                    if (c == ']') {
                        state = ParserState.START_CHILD;
                    }
                    else {
                        return null;
                    }
                    break;
            }  
        }
        return new JSONPath(children);
    }
    
    enum ParserState {
        START_EXPRESSION,
        START_CHILD,
        CHILD_NAME_DOT_NOTATION,
        START_CHILD_BRACKET_NOTATION,
        CHILD_NAME_BRACKET_NOTATION,
        END_CHILD_BRACKET_NOTATION
    }
}
