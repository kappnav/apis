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

import com.ibm.kappnav.logging.Logger;

/**
 * Tokenizes a command line string into its parameters.
 */
public class CommandLineTokenizer implements Iterable<String> {
    
    private final List<String> parameters;
    
    public CommandLineTokenizer(String commandLine) {
        if (Logger.isDebugEnabled()) {
            Logger.log(CommandLineTokenizer.class.getName(), "CommandLineTokenizer", Logger.LogType.DEBUG, "For commandLine=" + commandLine);
        }
        if (commandLine.isEmpty()) {
            parameters = Collections.emptyList();
            return;
        }
        parameters = new ArrayList<>();
        int mark = 0;
        boolean inQuotedString = false;
        final int length = commandLine.length();
        for (int i = 0; i < length; ++i) {
            final char c = commandLine.charAt(i);
            if (!inQuotedString) {
                // Search for space or double-quote character.
                inQuotedString = (c == '"');
                if (Character.isWhitespace(c) || inQuotedString) {
                    if (i > mark) {
                        // Reached end of command parameter.
                        parameters.add(commandLine.substring(mark, i));
                    }
                    mark = i + 1;
                }
                // Reached the end of the string.
                else if (i == length - 1) {
                    parameters.add(commandLine.substring(mark, i + 1));
                }
            }
            else {
                inQuotedString = (c != '"');
                if (!inQuotedString) {
                    if (i > mark) {
                        // Reached end of command parameter.
                        parameters.add(commandLine.substring(mark, i));
                    }
                    mark = i + 1;
                }
                // Reached the end of the string.
                else if (i == length - 1) {
                    parameters.add(commandLine.substring(mark, i + 1));
                }
            }
        }
    }

    @Override
    public Iterator<String> iterator() {
        return parameters.iterator();
    }
    
    public int getParameterCount() {
        return parameters.size();
    }
}
