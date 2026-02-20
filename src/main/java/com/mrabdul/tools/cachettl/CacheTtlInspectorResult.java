package com.mrabdul.tools.cachettl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CacheTtlInspectorResult {

    private final CacheTtlInspectorJsonReport report;
    private final List<CacheTtlFinding> findings;

    public CacheTtlInspectorResult(CacheTtlInspectorJsonReport report, List<CacheTtlFinding> findings) {
        this.report = report;
        this.findings = findings == null ? Collections.<CacheTtlFinding>emptyList() : findings;
    }

    public CacheTtlInspectorJsonReport getReport() { return report; }
    public List<CacheTtlFinding> getFindings() { return findings; }
    public int getFindingsCount() { return findings.size(); }

    public String toReportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Cache TTL Inspector v0.1 (config-driven) ===\n");
        sb.append("Scanned files: ").append(report.scannedFileCount).append("\n");
        sb.append("Operations: ").append(report.operationsCount).append("\n");
        sb.append("Namespaces: ").append(report.namespacesCount).append("\n");
        sb.append("Findings: ").append(findings.size()).append("\n\n");

        // summary buckets
        sb.append("Top namespaces by PUT count:\n");
        List<CacheNamespaceSummary> top = new ArrayList<CacheNamespaceSummary>(report.namespaces);
        Collections.sort(top, (a, b) -> Long.compare(b.putCount, a.putCount));
        for (int i = 0; i < Math.min(10, top.size()); i++) {
            CacheNamespaceSummary ns = top.get(i);
            sb.append("- ").append(ns.namespaceKey).append(" [layer=").append(ns.cacheLayer).append("]")
                    .append(" puts=").append(ns.putCount)
                    .append(" deletes=").append(ns.deleteCount)
                    .append(" ttlKnown=").append(ns.putWithTtlCount)
                    .append(" ttlMissing=").append(ns.putWithoutTtlCount)
                    .append("\n");
        }

        if (!findings.isEmpty()) {
            sb.append("\nFindings (top 50):\n");
            int lim = Math.min(50, findings.size());
            for (int i = 0; i < lim; i++) {
                CacheTtlFinding f = findings.get(i);
                sb.append("- [").append(f.severity).append("] ").append(f.kind)
                        .append(" | ").append(f.namespaceKey)
                        .append(" | ").append(f.file).append(":").append(f.line)
                        .append("\n  ").append(f.message).append("\n");
            }
        }

        return sb.toString();
    }
}