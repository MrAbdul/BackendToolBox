package com.mrabdul.tools.jdbcdetector;

import java.util.List;

public class JdbcDetectorResult {
    private final List<Finding> findings;
    private final long issueCount;
    private final long warnCount;
    private final long parseErrorCount;
    private final String reportText;

    public JdbcDetectorResult(List<Finding> findings,
                              long issueCount,
                              long warnCount,
                              long parseErrorCount,
                              String reportText) {
        this.findings = findings;
        this.issueCount = issueCount;
        this.warnCount = warnCount;
        this.parseErrorCount = parseErrorCount;
        this.reportText = reportText;
    }

    public List<Finding> getFindings() { return findings; }
    public long getIssueCount() { return issueCount; }
    public long getWarnCount() { return warnCount; }
    public long getParseErrorCount() { return parseErrorCount; }
    public String getReportText() { return reportText; }

    public boolean isOk() { return issueCount == 0; }
}
