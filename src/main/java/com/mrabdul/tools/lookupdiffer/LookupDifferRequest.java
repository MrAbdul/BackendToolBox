package com.mrabdul.tools.lookupdiffer;

public class LookupDifferRequest {
    private final String sourceDir;
    private final String targetDir;

    private final boolean caseInsensitive;
    private final String tableNameContains; // optional substring filter

    private final String outDir;   // optional
    private final String jsonOut;  // optional

    public LookupDifferRequest(String sourceDir,
                               String targetDir,
                               boolean caseInsensitive,
                               String tableNameContains,
                               String outDir,
                               String jsonOut) {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.caseInsensitive = caseInsensitive;
        this.tableNameContains = tableNameContains;
        this.outDir = outDir;
        this.jsonOut = jsonOut;
    }

    public String getSourceDir() { return sourceDir; }
    public String getTargetDir() { return targetDir; }
    public boolean isCaseInsensitive() { return caseInsensitive; }
    public String getTableNameContains() { return tableNameContains; }
    public String getOutDir() { return outDir; }
    public String getJsonOut() { return jsonOut; }
}