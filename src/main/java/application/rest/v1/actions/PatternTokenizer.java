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
import java.util.Iterator;
import java.util.List;

/**
 * Tokenizes a pattern string into literal and pattern segments.
 */
public class PatternTokenizer implements Iterable<PatternTokenizer.Token> {
    
    private final List<Token> tokens;
    
    public PatternTokenizer(String v) {
        tokens = new ArrayList<>();
        int mark = 0;
        int patternDepth = 0;
        final int length = v.length();
        for (int i = 0; i < length; ++i) {
            // Search for start of pattern segment: "${"
            if (i < length - 1 && v.charAt(i) == '$' && v.charAt(i + 1) == '{') {
                if (patternDepth++ == 0 && i > mark) {
                    // Reached end of literal segment.
                    tokens.add(new Token(v.substring(mark, i), false));
                    mark = i;
                }
            }
            // Search for end of pattern segment: "}"
            else if (v.charAt(i) == '}' && patternDepth > 0) {
                if (--patternDepth == 0) {
                    // Reached end of pattern segment.
                    tokens.add(new Token(v.substring(mark + 2, i), true));
                    mark = i + 1;
                }
                // Reached the end of the string.
                else if (i == length - 1) {
                    tokens.add(new Token(v.substring(mark, i + 1), false));
                }
            }
            // Reached the end of the string.
            else if (i == length - 1) {
                tokens.add(new Token(v.substring(mark, i + 1), false));
            }
        }
    }
    
    @Override
    public Iterator<PatternTokenizer.Token> iterator() {
        return tokens.iterator();
    }
    
    public static class Token {
        private static final char ENCODING_SEPERATOR = '~';
        private final String value;
        private final boolean isPattern;
        private final boolean isEncoded;
        public Token(String value, boolean isPattern) {
            this.value = value;
            this.isPattern = isPattern;
            this.isEncoded = !isPattern ? value.indexOf(ENCODING_SEPERATOR) != -1 : false;
        }
        public boolean isPattern() {
            return isPattern;
        }
        public boolean isEncoded() {
            return isEncoded;
        }
        public String getValue() {
            return value;
        }
        public String getDecodedValue() {
            if (isEncoded) {
                // Decode the escaped characters: ~nn. Where n is a hex character.
                int mark = 0;
                int length = value.length();
                StringBuilder result = new StringBuilder(value.length());
                for (int i = 0; i < length; ++i) {
                    if (i < length - 2 && value.charAt(i) == ENCODING_SEPERATOR) {
                        try {
                            char c = (char) Integer.valueOf(value.substring(i+1, i+3), 16).intValue();
                            if (i > mark) {
                                result.append(value, mark, i);
                            }
                            result.append(c);
                            mark = i + 3;
                        }
                        catch (NumberFormatException e) {}
                    }
                    // Reached the end of the string.
                    else if (i == length - 1) {
                        result.append(value, mark, i + 1);
                    }
                }
                return result.toString();
            }
            return value;
        }
        public void writeTo(StringBuilder sb) {
            if (isPattern) {
                sb.append("${");
            }
            sb.append(value);
            if (isPattern) {
                sb.append('}');
            }
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            writeTo(sb);
            return sb.toString();
        }
    }
}
