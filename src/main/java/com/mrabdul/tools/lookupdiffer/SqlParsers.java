package com.mrabdul.tools.lookupdiffer;

import java.util.ArrayList;
import java.util.List;

public final class SqlParsers {
    private SqlParsers() {}

    /** Normalize identifier token: trim + collapse spaces if NOT a string literal */
    public static String normToken(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("'") && t.endsWith("'") && t.length() >= 2) {
            return t;
        }
        return t.replaceAll("\\s+", " ");
    }

    /** Extracts clean table/column name, stripping schema prefixes and quotes.
     * E.g. "BBYNIB"."LOOKUP_PRODUCT_CLASS_NAMES" -> LOOKUP_PRODUCT_CLASS_NAMES
     */
    public static String cleanIdentifier(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1).trim();
        }
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
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

            // handle quote toggle
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
        return normalizeSql(s);
    }

    /** Strips comments and collapses whitespace outside of quotes, preserving everything inside quotes. */
    public static String normalizeSql(String sql) {
        if (sql == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;
        boolean lastWasWhitespace = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (inSingleLineComment) {
                if (c == '\n' || c == '\r') inSingleLineComment = false;
                continue;
            }
            if (inMultiLineComment) {
                if (c == '*' && next == '/') {
                    inMultiLineComment = false;
                    i++;
                }
                continue;
            }

            if (!inDoubleQuote && c == '\'') {
                inSingleQuote = !inSingleQuote;
                sb.append(c);
                lastWasWhitespace = false;
                continue;
            } else if (!inSingleQuote && c == '"') {
                inDoubleQuote = !inDoubleQuote;
                sb.append(c);
                lastWasWhitespace = false;
                continue;
            }

            if (inSingleQuote || inDoubleQuote) {
                sb.append(c);
                lastWasWhitespace = false;
                continue;
            }

            // Outside quotes
            if (c == '-' && next == '-') {
                inSingleLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inMultiLineComment = true;
                i++;
                continue;
            }

            if (Character.isWhitespace(c)) {
                if (!lastWasWhitespace) {
                    sb.append(' ');
                    lastWasWhitespace = true;
                }
            } else {
                sb.append(c);
                lastWasWhitespace = false;
            }
        }
        return sb.toString().trim();
    }

    public static String stripComments(String sql) {
        if (sql == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (inSingleLineComment) {
                if (c == '\n' || c == '\r') {
                    inSingleLineComment = false;
                    sb.append(c);
                }
                continue;
            }
            if (inMultiLineComment) {
                if (c == '*' && next == '/') {
                    inMultiLineComment = false;
                    i++;
                }
                continue;
            }

            if (!inDoubleQuote && c == '\'') {
                inSingleQuote = !inSingleQuote;
            } else if (!inSingleQuote && c == '"') {
                inDoubleQuote = !inDoubleQuote;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '-' && next == '-') {
                    inSingleLineComment = true;
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    inMultiLineComment = true;
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<String>();
        if (sql == null) return out;

        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inSingleComment = false;
        boolean inMultiComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (inSingleComment) {
                cur.append(c);
                if (c == '\n' || c == '\r') inSingleComment = false;
                continue;
            }
            if (inMultiComment) {
                cur.append(c);
                if (c == '*' && next == '/') {
                    cur.append(next);
                    inMultiComment = false;
                    i++;
                }
                continue;
            }

            if (c == '\'' && !inDouble) inSingle = !inSingle;
            if (c == '"' && !inSingle) inDouble = !inDouble;

            if (!inSingle && !inDouble) {
                if (c == '-' && next == '-') {
                    inSingleComment = true;
                } else if (c == '/' && next == '*') {
                    inMultiComment = true;
                } else if (c == ';') {
                    cur.append(c);
                    out.add(cur.toString());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }

        String last = cur.toString().trim();
        if (!last.isEmpty()) out.add(last);

        return out;
    }
}