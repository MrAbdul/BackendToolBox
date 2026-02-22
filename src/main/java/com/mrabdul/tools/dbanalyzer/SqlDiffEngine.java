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

        // Track unmatched
        List<SqlArtifact> unmatchedBase = new ArrayList<>();
        List<SqlArtifact> unmatchedTarget = new ArrayList<>();

        for (String k : allKeys) {
            SqlArtifact b = baseByKey.get(k);
            SqlArtifact t = targetByKey.get(k);

            if (b == null) { unmatchedTarget.add(t); continue; }
            if (t == null) { unmatchedBase.add(b); continue; }

            if (!Objects.equals(b.getNormalizedSql(), t.getNormalizedSql())) {
                Map<String, Set<String>> newCols = computeNewColumns(b.getMeta(), t.getMeta());
                changes.add(new DbAnalyzerResult.Change(DbAnalyzerResult.Change.Kind.MODIFIED, prettyKey(t), b, t, newCols));
            }
        }

        // --- Second pass: pair by content to avoid fake ADDED/REMOVED ---
        Map<String, Deque<SqlArtifact>> baseByFingerprint = new HashMap<>();
        for (SqlArtifact b : unmatchedBase) {
            String fp = fingerprint(b);
            baseByFingerprint.computeIfAbsent(fp, x -> new ArrayDeque<>()).add(b);
        }

        Set<SqlArtifact> pairedBase = new HashSet<>();
        Set<SqlArtifact> pairedTarget = new HashSet<>();

        for (SqlArtifact t : unmatchedTarget) {
            Deque<SqlArtifact> q = baseByFingerprint.get(fingerprint(t));
            if (q != null && !q.isEmpty()) {
                SqlArtifact b = q.removeFirst();
                pairedBase.add(b);
                pairedTarget.add(t);

                // Option 1: suppress completely (recommended: no noise)
                // do nothing

                // Option 2: or record as MODIFIED with empty diff (acts like MOVED)
                // changes.add(new DbAnalyzerResult.Change(DbAnalyzerResult.Change.Kind.MODIFIED, prettyKey(t), b, t, Collections.emptyMap()));
            }
        }

        // Emit real removed/added that were not paired by content
        for (SqlArtifact b : unmatchedBase) {
            if (!pairedBase.contains(b)) {
                changes.add(new DbAnalyzerResult.Change(DbAnalyzerResult.Change.Kind.REMOVED, prettyKey(b), b, null, null));
            }
        }
        for (SqlArtifact t : unmatchedTarget) {
            if (!pairedTarget.contains(t)) {
                changes.add(new DbAnalyzerResult.Change(DbAnalyzerResult.Change.Kind.ADDED, prettyKey(t), null, t, null));
            }
        }

        return new DbAnalyzerResult(changes, base.size(), target.size());
    }

    private String fingerprint(SqlArtifact a) {
        if (a == null) return "";
        // include type to avoid pairing unrelated queries that normalize similarly
        String type = (a.getMeta() != null && a.getMeta().getType() != null)
                ? a.getMeta().getType().name()
                : "UNKNOWN";
        return type + "|" + a.getNormalizedSql();
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
