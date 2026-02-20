package com.mrabdul.tools.dbanalyzer;

public final class SqlNormalizer {

    private SqlNormalizer() {}

    public static String normalize(String sql) {
        if (sql == null) return "";
        String s = sql;

        // Strip block comments /* ... */
        s = s.replaceAll("(?s)/\\*.*?\\*/", " ");

        // Strip line comments -- ...
        s = s.replaceAll("(?m)--.*?$", " ");

        // Collapse whitespace
        s = s.replaceAll("\\s+", " ").trim();

        // Replace string literals '...'
        s = s.replaceAll("'([^']|'')*'", "?");

        // Replace numeric literals (basic)
        s = s.replaceAll("\\b\\d+(\\.\\d+)?\\b", "?");

        // Uppercase for stable diff
        s = s.toUpperCase();

        return s;
    }

    public static boolean looksLikeSql(String s) {
        if (s == null) return false;
        String t = s.trim().toUpperCase();
        if (t.isEmpty()) return false;

        // Common starters
        if (t.startsWith("SELECT ") || t.startsWith("UPDATE ") || t.startsWith("INSERT ") || t.startsWith("DELETE ") || t.startsWith("WITH ")) {
            return true;
        }

        // Sometimes query starts with hint or parentheses
        if (t.startsWith("/*+") && (t.contains("SELECT ") || t.contains("UPDATE ") || t.contains("INSERT ") || t.contains("DELETE "))) {
            return true;
        }

        // Heuristic: has FROM/INTO/SET patterns
        if ((t.contains(" FROM ") && t.contains("SELECT")) ||
                (t.contains(" INTO ") && t.contains("INSERT")) ||
                (t.contains(" SET ") && t.contains("UPDATE")) ||
                (t.contains("DELETE") && t.contains(" FROM "))) {
            return true;
        }

        return false;
    }
}
