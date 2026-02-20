package com.mrabdul.tools.dbanalyzer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlHeuristicParser {

    // Captures identifiers like SCHEMA.TABLE or TABLE or "QuotedName"
    private static final Pattern IDENT = Pattern.compile("\"[^\"]+\"|[A-Z_][A-Z0-9_\\$#]*?(?:\\.[A-Z_][A-Z0-9_\\$#]*)?");

    public SqlMeta parse(String normalizedSql) {
        if (normalizedSql == null || normalizedSql.isEmpty()) return SqlMeta.unknown();
        String s = normalizedSql.trim();

        SqlMeta.Type type = detectType(s);

        try {
            List<String> tables = extractTables(type, s);
            Map<String, String> aliasToTable = extractAliases(type, s, tables);
            Map<String, Set<String>> cols = extractColumns(type, s, tables, aliasToTable);

            return new SqlMeta(type, tables, cols, true);
        } catch (Exception e) {
            // Heuristic parser should never kill analysis
            return new SqlMeta(type, extractTablesSafe(type, s), Collections.emptyMap(), false);
        }
    }

    private SqlMeta.Type detectType(String s) {
        if (s.startsWith("SELECT ") || s.startsWith("WITH ")) return SqlMeta.Type.SELECT;
        if (s.startsWith("UPDATE ")) return SqlMeta.Type.UPDATE;
        if (s.startsWith("INSERT ")) return SqlMeta.Type.INSERT;
        if (s.startsWith("DELETE ")) return SqlMeta.Type.DELETE;
        return SqlMeta.Type.UNKNOWN;
    }

    private List<String> extractTablesSafe(SqlMeta.Type type, String s) {
        try { return extractTables(type, s); } catch (Exception ignored) { return Collections.emptyList(); }
    }

    private List<String> extractTables(SqlMeta.Type type, String s) {
        List<String> tables = new ArrayList<>();

        if (type == SqlMeta.Type.UPDATE) {
            String after = afterToken(s, "UPDATE");
            String t = firstIdent(after);
            if (t != null) tables.add(cleanIdent(t));
        } else if (type == SqlMeta.Type.INSERT) {
            String after = afterToken(s, "INTO");
            String t = firstIdent(after);
            if (t != null) tables.add(cleanIdent(t));
        } else if (type == SqlMeta.Type.DELETE) {
            String after = afterToken(s, "FROM");
            String t = firstIdent(after);
            if (t != null) tables.add(cleanIdent(t));
        } else if (type == SqlMeta.Type.SELECT) {
            // collect FROM + JOIN tables
            collectTablesAfterKeyword(s, "FROM", tables);
            collectTablesAfterKeyword(s, "JOIN", tables);
        }

        // de-dup preserve order
        LinkedHashSet<String> uniq = new LinkedHashSet<>(tables);
        return new ArrayList<>(uniq);
    }

    private void collectTablesAfterKeyword(String s, String kw, List<String> out) {
        int idx = indexOfWord(s, kw);
        while (idx >= 0) {
            String after = s.substring(idx + kw.length()).trim();

            // stop if subquery
            if (after.startsWith("(")) {
                idx = indexOfWord(s, kw, idx + kw.length());
                continue;
            }

            // can be comma-separated: FROM A, B
            // take first ident as table, then if comma, keep scanning
            Matcher m = IDENT.matcher(after);
            if (m.find()) {
                String t = cleanIdent(m.group());
                out.add(t);

                // also try to catch comma list quickly
                int commaIdx = after.indexOf(",");
                if (commaIdx > 0 && commaIdx < 120) {
                    String rest = after.substring(commaIdx + 1);
                    Matcher m2 = IDENT.matcher(rest);
                    if (m2.find()) out.add(cleanIdent(m2.group()));
                }
            }

            idx = indexOfWord(s, kw, idx + kw.length());
        }
    }

    private Map<String, String> extractAliases(SqlMeta.Type type, String s, List<String> tables) {
        Map<String, String> aliasToTable = new HashMap<>();
        if (type != SqlMeta.Type.SELECT) return aliasToTable;

        // crude: after FROM or JOIN: TABLE [AS] ALIAS
        // We'll scan tokens around FROM/JOIN occurrences.
        aliasToTable.putAll(scanAliasAfterKeyword(s, "FROM"));
        aliasToTable.putAll(scanAliasAfterKeyword(s, "JOIN"));

        // also map table name itself as "alias" so QUALIFIER.TABLE works
        for (String t : tables) {
            String shortName = t.contains(".") ? t.substring(t.indexOf('.') + 1) : t;
            aliasToTable.put(shortName, t);
            aliasToTable.put(t, t);
        }

        return aliasToTable;
    }

    private Map<String, String> scanAliasAfterKeyword(String s, String kw) {
        Map<String, String> map = new HashMap<>();
        int idx = indexOfWord(s, kw);
        while (idx >= 0) {
            String after = s.substring(idx + kw.length()).trim();
            Matcher m = IDENT.matcher(after);
            if (m.find()) {
                String table = cleanIdent(m.group());
                String rest = after.substring(m.end()).trim();

                // alias is first ident after table unless it's ON/WHERE/GROUP/ORDER/INNER/LEFT/RIGHT/FULL
                Matcher m2 = IDENT.matcher(rest);
                if (m2.find()) {
                    String alias = cleanIdent(m2.group());
                    if (!isStopWord(alias)) {
                        map.put(alias, table);
                    }
                }
            }
            idx = indexOfWord(s, kw, idx + kw.length());
        }
        return map;
    }

    private Map<String, Set<String>> extractColumns(SqlMeta.Type type, String s, List<String> tables, Map<String, String> aliasToTable) {
        Map<String, Set<String>> cols = new HashMap<>();

        if (type == SqlMeta.Type.INSERT) {
            // INSERT INTO T (A,B,C) VALUES ...
            String into = afterToken(s, "INTO");
            String table = cleanIdent(firstIdent(into));
            String afterTable = table == null ? "" : into.substring(into.indexOf(table) + table.length());
            int open = afterTable.indexOf("(");
            int close = afterTable.indexOf(")");
            if (open >= 0 && close > open) {
                String inside = afterTable.substring(open + 1, close);
                for (String c : splitComma(inside)) addCol(cols, table, cleanIdent(c));
            }
            return cols;
        }

        if (type == SqlMeta.Type.UPDATE) {
            // UPDATE T SET A=?, B=? WHERE ...
            String afterUpdate = afterToken(s, "UPDATE");
            String table = cleanIdent(firstIdent(afterUpdate));
            String afterSet = afterToken(s, "SET");
            String setPart = beforeAnyKeyword(afterSet, "WHERE", "RETURNING");
            for (String assignment : splitComma(setPart)) {
                String left = assignment.split("=", 2)[0].trim();
                String col = left.contains(".") ? left.substring(left.indexOf('.') + 1) : left;
                addCol(cols, table, cleanIdent(col));
            }
            return cols;
        }

        if (type == SqlMeta.Type.SELECT) {
            // SELECT <cols> FROM ...
            String selectPart = afterToken(s, "SELECT");
            String proj = beforeAnyKeyword(selectPart, "FROM");
            for (String expr : splitComma(proj)) {
                String e = expr.trim();
                if (e.equals("*") || e.endsWith(".*")) continue;

                // try to capture alias.column tokens
                Matcher m = Pattern.compile("\\b([A-Z_][A-Z0-9_\\$#]*)\\.([A-Z_][A-Z0-9_\\$#]*)\\b").matcher(e);
                boolean anyQualified = false;
                while (m.find()) {
                    anyQualified = true;
                    String alias = m.group(1);
                    String col = m.group(2);
                    String table = aliasToTable.getOrDefault(alias, "UNKNOWN");
                    addCol(cols, table, cleanIdent(col));
                }

                if (!anyQualified) {
                    // unqualified identifier in projection: if single table, assign, else UNKNOWN
                    Matcher id = Pattern.compile("\\b([A-Z_][A-Z0-9_\\$#]*)\\b").matcher(e);
                    while (id.find()) {
                        String tok = id.group(1);
                        if (isStopWord(tok)) continue;
                        if (tok.equals("NULL")) continue;
                        if (tables.size() == 1) addCol(cols, tables.get(0), tok);
                        else addCol(cols, "UNKNOWN", tok);
                    }
                }
            }
            return cols;
        }

        return cols;
    }

    private void addCol(Map<String, Set<String>> cols, String table, String col) {
        if (table == null || table.isEmpty() || col == null || col.isEmpty()) return;
        cols.computeIfAbsent(table, k -> new LinkedHashSet<>()).add(col);
    }

    private static List<String> splitComma(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;

        // naive split (good enough v0.1). We assume projection/column lists aren't full of nested commas.
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String firstIdent(String s) {
        if (s == null) return null;
        Matcher m = IDENT.matcher(s.trim());
        if (m.find()) return m.group();
        return null;
    }

    private static String cleanIdent(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String afterToken(String s, String token) {
        int idx = indexOfWord(s, token);
        if (idx < 0) return "";
        return s.substring(idx + token.length()).trim();
    }

    private static String beforeAnyKeyword(String s, String... kws) {
        String t = s;
        int best = -1;
        for (String kw : kws) {
            int idx = indexOfWord(t, kw);
            if (idx >= 0 && (best < 0 || idx < best)) best = idx;
        }
        return best >= 0 ? t.substring(0, best).trim() : t.trim();
    }

    private static int indexOfWord(String s, String word) {
        return indexOfWord(s, word, 0);
    }

    private static int indexOfWord(String s, String word, int from) {
        // word boundary-ish search
        return s.indexOf(" " + word + " ", from) >= 0 ? s.indexOf(" " + word + " ", from) + 1
                : (s.startsWith(word + " ") && from == 0 ? 0 : s.indexOf(word + " ", from));
    }

    private static boolean isStopWord(String t) {
        if (t == null) return true;
        switch (t) {
            case "SELECT": case "FROM": case "WHERE": case "AND": case "OR":
            case "JOIN": case "LEFT": case "RIGHT": case "INNER": case "OUTER":
            case "ON": case "AS": case "GROUP": case "BY": case "ORDER":
            case "DISTINCT": case "CASE": case "WHEN": case "THEN": case "ELSE": case "END":
            case "UPDATE": case "SET": case "INSERT": case "INTO": case "DELETE":
            case "VALUES": case "LIKE": case "IN": case "IS": case "NOT":
            case "NULL": case "NVL": case "COALESCE": case "TO_DATE": case "TO_CHAR":
            case "COUNT": case "SUM": case "MIN": case "MAX": case "AVG":
                return true;
            default:
                return false;
        }
    }
}
