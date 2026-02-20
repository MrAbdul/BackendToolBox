package com.mrabdul.tools.dbanalyzer;

import java.util.*;

public class SqlDiffEngine {

    public DbAnalyzerResult diff(List<SqlArtifact> base, List<SqlArtifact> target) {

        Map<String, SqlArtifact> baseByKey = new HashMap<>();
        for (SqlArtifact a : base) baseByKey.put(a.getKey(), a);

        Map<String, SqlArtifact> targetByKey = new HashMap<>();
        for (SqlArtifact a : target) targetByKey.put(a.getKey(), a);

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(baseByKey.keySet());
        allKeys.addAll(targetByKey.keySet());

        List<DbAnalyzerResult.Change> changes = new ArrayList<>();

        for (String k : allKeys) {
            SqlArtifact b = baseByKey.get(k);
            SqlArtifact t = targetByKey.get(k);

            if (b == null) {
                changes.add(new DbAnalyzerResult.Change(DbAnalyzerResult.Change.Kind.ADDED, prettyKey(t), null, t, null));
                continue;
            }
            if (t == null) {
                changes.add(new DbAnalyzerResult.Change(DbAnalyzerResult.Change.Kind.REMOVED, prettyKey(b), b, null, null));
                continue;
            }

            if (!Objects.equals(b.getNormalizedSql(), t.getNormalizedSql())) {
                Map<String, Set<String>> newCols = computeNewColumns(b.getMeta(), t.getMeta());
                changes.add(new DbAnalyzerResult.Change(DbAnalyzerResult.Change.Kind.MODIFIED, prettyKey(t), b, t, newCols));
            }
        }

        return new DbAnalyzerResult(changes, base.size(), target.size());
    }

    private String prettyKey(SqlArtifact a) {
        if (a == null) return "";
        return a.getRelativeFile() + ":" + a.getLine() + " (" + a.getClassName() + " " + a.getMethodOrField() + ")";
    }

    private Map<String, Set<String>> computeNewColumns(SqlMeta base, SqlMeta target) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        Map<String, Set<String>> b = base == null ? Collections.emptyMap() : base.getColumnsByTable();
        Map<String, Set<String>> t = target == null ? Collections.emptyMap() : target.getColumnsByTable();

        for (Map.Entry<String, Set<String>> e : t.entrySet()) {
            String table = e.getKey();
            Set<String> tgtCols = e.getValue() == null ? Collections.emptySet() : e.getValue();
            Set<String> baseCols = b.getOrDefault(table, Collections.emptySet());

            Set<String> diff = new LinkedHashSet<>(tgtCols);
            diff.removeAll(baseCols);

            if (!diff.isEmpty()) out.put(table, diff);
        }

        return out;
    }
}