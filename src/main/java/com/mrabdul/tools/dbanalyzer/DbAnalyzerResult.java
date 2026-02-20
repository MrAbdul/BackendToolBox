package com.mrabdul.tools.dbanalyzer;

import java.util.*;
import java.util.stream.Collectors;

public class DbAnalyzerResult {

    public static class Change {
        public enum Kind { ADDED, REMOVED, MODIFIED }

        private final Kind kind;
        private final String key;
        private final SqlArtifact base;
        private final SqlArtifact target;
        private final Map<String, Set<String>> newColumnsByTable; // only for MODIFIED

        public Change(Kind kind, String key, SqlArtifact base, SqlArtifact target, Map<String, Set<String>> newColumnsByTable) {
            this.kind = kind;
            this.key = key;
            this.base = base;
            this.target = target;
            this.newColumnsByTable = newColumnsByTable == null ? Collections.emptyMap() : newColumnsByTable;
        }

        public Kind getKind() { return kind; }
        public String getKey() { return key; }
        public SqlArtifact getBase() { return base; }
        public SqlArtifact getTarget() { return target; }
        public Map<String, Set<String>> getNewColumnsByTable() { return newColumnsByTable; }
    }

    private final List<Change> changes;
    private final int baseSqlCount;
    private final int targetSqlCount;

    public DbAnalyzerResult(List<Change> changes, int baseSqlCount, int targetSqlCount) {
        this.changes = changes;
        this.baseSqlCount = baseSqlCount;
        this.targetSqlCount = targetSqlCount;
    }

    public List<Change> getChanges() { return changes; }

    public boolean hasSchemaRelevantChanges() {
        return !changes.isEmpty();
    }

    public String toReport() {
        long added = changes.stream().filter(c -> c.getKind() == Change.Kind.ADDED).count();
        long removed = changes.stream().filter(c -> c.getKind() == Change.Kind.REMOVED).count();
        long modified = changes.stream().filter(c -> c.getKind() == Change.Kind.MODIFIED).count();

        StringBuilder sb = new StringBuilder();
        sb.append("DBAnalyzer v0.1 (SQL-in-code diff)\n");
        sb.append("Base SQL artifacts: ").append(baseSqlCount).append("\n");
        sb.append("Target SQL artifacts: ").append(targetSqlCount).append("\n");
        sb.append("Changes: modified=").append(modified).append(" added=").append(added).append(" removed=").append(removed).append("\n\n");

        for (Change c : changes) {
            sb.append("[").append(c.getKind()).append("] ").append(c.getKey()).append("\n");

            if (c.getKind() == Change.Kind.MODIFIED && c.getTarget() != null) {
                SqlArtifact t = c.getTarget();
                sb.append("  Type: ").append(t.getMeta().getType()).append("\n");
                sb.append("  Tables: ").append(String.join(", ", t.getMeta().getTables())).append("\n");

                if (!c.getNewColumnsByTable().isEmpty()) {
                    sb.append("  New columns:\n");
                    for (Map.Entry<String, Set<String>> e : c.getNewColumnsByTable().entrySet()) {
                        sb.append("    ").append(e.getKey()).append(": ")
                                .append(e.getValue().stream().sorted().collect(Collectors.joining(", ")))
                                .append("\n");
                    }
                } else {
                    sb.append("  New columns: (none detected by heuristics)\n");
                }

                sb.append("  Old(norm): ").append(shorten(c.getBase().getNormalizedSql())).append("\n");
                sb.append("  New(norm): ").append(shorten(c.getTarget().getNormalizedSql())).append("\n");
            } else if (c.getKind() == Change.Kind.ADDED && c.getTarget() != null) {
                sb.append("  Type: ").append(c.getTarget().getMeta().getType()).append("\n");
                sb.append("  Tables: ").append(String.join(", ", c.getTarget().getMeta().getTables())).append("\n");
                sb.append("  SQL(norm): ").append(shorten(c.getTarget().getNormalizedSql())).append("\n");
            } else if (c.getKind() == Change.Kind.REMOVED && c.getBase() != null) {
                sb.append("  Type: ").append(c.getBase().getMeta().getType()).append("\n");
                sb.append("  Tables: ").append(String.join(", ", c.getBase().getMeta().getTables())).append("\n");
                sb.append("  SQL(norm): ").append(shorten(c.getBase().getNormalizedSql())).append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= 220) return s;
        return s.substring(0, 220) + "...";
    }
}
