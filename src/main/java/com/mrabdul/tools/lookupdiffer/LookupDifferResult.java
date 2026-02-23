package com.mrabdul.tools.lookupdiffer;

import java.util.List;

public class LookupDifferResult {
    private final List<DiffFinding> findings;

    private final long missingTables;
    private final long missingColumns;
    private final long missingRows;
    private final long mismatchedRows;
    private final long warnings;
    private final long parseErrors;

    private final String reportText;

    public LookupDifferResult(List<DiffFinding> findings,
                              long missingTables,
                              long missingColumns,
                              long missingRows,
                              long mismatchedRows,
                              long warnings,
                              long parseErrors,
                              String reportText) {
        this.findings = findings;
        this.missingTables = missingTables;
        this.missingColumns = missingColumns;
        this.missingRows = missingRows;
        this.mismatchedRows = mismatchedRows;
        this.warnings = warnings;
        this.parseErrors = parseErrors;
        this.reportText = reportText;
    }

    public List<DiffFinding> getFindings() { return findings; }
    public long getMissingTables() { return missingTables; }
    public long getMissingColumns() { return missingColumns; }
    public long getMissingRows() { return missingRows; }
    public long getMismatchedRows() { return mismatchedRows; }
    public long getWarnings() { return warnings; }
    public long getParseErrors() { return parseErrors; }
    public String getReportText() { return reportText; }

    public boolean isOk() {
        return missingTables == 0 && missingColumns == 0 && missingRows == 0 && mismatchedRows == 0 && parseErrors == 0;
    }
}