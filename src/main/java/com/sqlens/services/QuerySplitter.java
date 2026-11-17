package com.sqlens.services;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a .sql file into statements on ';', ignoring semicolons inside string
 * literals, quoted identifiers and comments. Each piece is still validated separately.
 */
public final class QuerySplitter {

    private QuerySplitter() {}

    public static List<String> split(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int n = script.length();
        int i = 0;
        while (i < n) {
            char c = script.charAt(i);
            char next = i + 1 < n ? script.charAt(i + 1) : '\0';
            int end;
            if (c == '\'' || c == '"' || c == '`') {
                end = i + 1;
                while (end < n) {
                    char d = script.charAt(end);
                    if (d == '\\' && c != '`') {
                        end += 2;
                        continue;
                    }
                    if (d == c) {
                        end++;
                        if (end < n && script.charAt(end) == c) {
                            end++;
                            continue;
                        }
                        break;
                    }
                    end++;
                }
            } else if (c == '#' || (c == '-' && next == '-')) {
                end = script.indexOf('\n', i);
                end = end < 0 ? n : end;
            } else if (c == '/' && next == '*') {
                end = script.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
            } else if (c == ';') {
                add(out, current);
                current.setLength(0);
                i++;
                continue;
            } else {
                end = i + 1;
            }
            end = Math.min(end, n);
            current.append(script, i, end);
            i = end;
        }
        add(out, current);
        return out;
    }

    private static void add(List<String> out, StringBuilder sb) {
        String s = sb.toString().strip();
        if (!s.isEmpty() && !onlyComments(s)) {
            out.add(s);
        }
    }

    private static boolean onlyComments(String s) {
        String stripped = s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)(--|#).*$", "").strip();
        return stripped.isEmpty();
    }
}
