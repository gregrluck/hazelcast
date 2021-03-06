/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hazelcast.util.StringUtil.lowerCaseInternal;

class Parser {
    private static final String SPLIT_EXPRESSION = " ";

    private static final Map<String, Integer> PRECEDENCE = new HashMap<String, Integer>();

    static {
        PRECEDENCE.put("(", 15);
        PRECEDENCE.put(")", 15);
        PRECEDENCE.put("not", 8);
        PRECEDENCE.put("=", 10);
        PRECEDENCE.put(">", 10);
        PRECEDENCE.put("<", 10);
        PRECEDENCE.put(">=", 10);
        PRECEDENCE.put("<=", 10);
        PRECEDENCE.put("==", 10);
        PRECEDENCE.put("!=", 10);
        PRECEDENCE.put("between", 10);
        PRECEDENCE.put("in", 10);
        PRECEDENCE.put("like", 10);
        PRECEDENCE.put("ilike", 10);
        PRECEDENCE.put("regex", 10);
        PRECEDENCE.put("and", 5);
        PRECEDENCE.put("or", 3);
    }

    private static final List<String> CHAR_OPERATORS
            = Arrays.asList("(", ")", " + ", " - ", "=", "<", ">", " * ", " / ", "!");

    private static final int NO_INDEX = -1;
    private static final String IN_LOWER = " in ";
    private static final String IN_LOWER_P = " in(";
    private static final String IN_UPPER = " IN ";
    private static final String IN_UPPER_P = " IN(";

    public Parser() {
    }

    public List<String> toPrefix(String in) {
        List<String> tokens = buildTokens(alignINClause(in));

        List<String> output = new ArrayList<String>();
        List<String> stack = new ArrayList<String>();
        for (String token : tokens) {
            if (isOperand(token)) {
                if (token.equals(")")) {
                    while (openParanthesesFound(stack)) {
                        output.add(stack.remove(stack.size() - 1));
                    }
                    if (stack.size() > 0) {
                        // temporarily fix for issue #189
                        stack.remove(stack.size() - 1);
                    }
                } else {
                    while (openParanthesesFound(stack) && !hasHigherPrecedence(token, stack.get(stack.size() - 1))) {
                        output.add(stack.remove(stack.size() - 1));
                    }
                    stack.add(token);
                }
            } else {
                output.add(token);
            }
        }
        while (stack.size() > 0) {
            output.add(stack.remove(stack.size() - 1));
        }
        return output;
    }

    private List<String> buildTokens(String in) {
        List<String> tokens = split(in);
        if (tokens.contains("between") || tokens.contains("BETWEEN")) {
            boolean found = true;
            boolean dirty = false;
            betweens:
            while (found) {
                for (int i = 0; i < tokens.size(); i++) {
                    if ("between".equalsIgnoreCase(tokens.get(i))) {
                        tokens.set(i, "betweenAnd");
                        tokens.remove(i + 2);
                        dirty = true;
                        continue betweens;
                    }
                }
                found = false;
            }
            if (dirty) {
                for (int i = 0; i < tokens.size(); i++) {
                    if ("betweenAnd".equals(tokens.get(i))) {
                        tokens.set(i, "between");
                    }
                }
            }
        }
        return tokens;
    }

    public List<String> split(String in) {
        final StringBuilder result = new StringBuilder();
        final char[] chars = in.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (CHAR_OPERATORS.contains(String.valueOf(c))) {
                if (i < chars.length - 2 && CHAR_OPERATORS.contains(String.valueOf(chars[i + 1]))
                        && !("(".equals(String.valueOf(chars[i + 1])) || ")".equals(String.valueOf(chars[i + 1])))) {
                    result.append(" ").append(c).append(chars[i + 1]).append(" ");
                    i++;
                } else {
                    result.append(" ").append(c).append(" ");
                }
            } else {
                result.append(c);
            }
        }
        final String[] tokens = result.toString().split(SPLIT_EXPRESSION);
        final List<String> list = new ArrayList<String>();
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].trim();
            if (!tokens[i].equals("")) {
                list.add(tokens[i]);
            }
        }
        return list;
    }

    boolean hasHigherPrecedence(String operator1, String operator2) {
        return PRECEDENCE.get(lowerCaseInternal(operator1)) > PRECEDENCE.get(lowerCaseInternal(operator2));
    }

    boolean isOperand(String string) {
        return PRECEDENCE.containsKey(lowerCaseInternal(string));
    }

    private boolean openParanthesesFound(List<String> stack) {
        return stack.size() > 0 && !stack.get(stack.size() - 1).equals("(");
    }

    /*
    *
    * Recursively finds in-clauses and reformats them for the parser
    *
    * */
    private String alignINClause(String in) {
        final int indexLowerIn = in.indexOf(IN_LOWER);
        final int indexLowerInWithParentheses = in.indexOf(IN_LOWER_P);
        final int indexUpperIn = in.indexOf(IN_UPPER);
        final int indexUpperInWithParentheses = in.indexOf(IN_UPPER_P);
        // find first occurrence of in clause.
        final int indexIn = findMinIfNot(indexUpperInWithParentheses,
                findMinIfNot(indexUpperIn,
                        findMinIfNot(indexLowerIn, indexLowerInWithParentheses, NO_INDEX), NO_INDEX), NO_INDEX
        );

        if (indexIn > NO_INDEX && (indexIn == indexLowerInWithParentheses || indexIn == indexUpperInWithParentheses)) {
            // 3 is the size of param in ending with a parentheses.
            // add SPLIT_EXPRESSION
            in = in.substring(0, indexIn + 3) + SPLIT_EXPRESSION + in.substring(indexIn + 3);
        }
        String sql = in;
        if (indexIn != NO_INDEX) {
            final int indexOpen = in.indexOf('(', indexIn);
            final int indexClose = in.indexOf(')', indexOpen);
            String sub = in.substring(indexOpen, indexClose + 1);
            sub = sub.replaceAll(" ", "");
            sql = in.substring(0, indexOpen) + sub
                    + alignINClause(in.substring(indexClose + 1));

        }
        return sql;
    }

    /**
     * finds min choosing a lower bound.
     *
     * @param a      first number
     * @param b      second number
     * @param notMin lower bound
     */
    private int findMinIfNot(int a, int b, int notMin) {
        if (a <= notMin) {
            return b;
        }
        if (b <= notMin) {
            return a;
        }
        return Math.min(a, b);
    }
}

