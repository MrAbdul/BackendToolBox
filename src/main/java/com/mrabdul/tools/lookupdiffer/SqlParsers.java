package com.mrabdul.tools.lookupdiffer;

import java.util.ArrayList;
import java.util.List;

public final class SqlParsers {
    private SqlParsers() {}

    /** Normalize identifier token: trim + collapse spaces + optionally upper-case later in engine */
    public static String normToken(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }

    /** Split by commas, respecting quotes and parentheses depth (for VALUES lists, column lists, etc.) */
    public static List<String> splitTopLevelComma(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) return out;

        String s = raw.trim();
        if (s.isEmpty()) return out;

        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        int paren = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            // handle quote toggle (very basic, but good enough for exports)
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                cur.append(c);
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                cur.append(c);
                continue;
            }

            if (!inSingle && !inDouble) {
                if (c == '(') paren++;
                if (c == ')') paren = Math.max(0, paren - 1);

                if (c == ',' && paren == 0) {
                    out.add(normToken(cur.toString()));
                    cur.setLength(0);
                    continue;
                }
            }

            cur.append(c);
        }

        String last = normToken(cur.toString());
        if (!last.isEmpty()) out.add(last);

        return out;
    }

    /** Find substring between first occurrence of openChar and its matching closeChar. Returns null if not found. */
    public static String firstBalanced(String s, char openChar, char closeChar) {
        if (s == null) return null;
        int start = s.indexOf(openChar);
        if (start < 0) return null;

        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;

        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '\'' && !inDouble) inSingle = !inSingle;
            if (c == '"' && !inSingle) inDouble = !inDouble;

            if (inSingle || inDouble) continue;

            if (c == openChar) {
                if (depth == 0) start = i;
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return s.substring(start + 1, i);
                }
            }
        }
        return null;
    }

    public static String flattenSqlLine(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
    }
}