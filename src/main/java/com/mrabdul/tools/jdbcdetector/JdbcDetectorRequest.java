package com.mrabdul.tools.jdbcdetector;

import java.util.List;

public class JdbcDetectorRequest {
    private final String sourceRootPath;

    // If empty => analyze EVERYTHING.
    // If provided => analyze only classes that extend ANY of these.
    private final List<String> daoBaseTypes;

    private final boolean includeWarnings;
    private final boolean includeParseErrors;

    // Optional (null/empty means "no JSON")
    private final String jsonOutputPath;

    public JdbcDetectorRequest(String sourceRootPath,
                               List<String> daoBaseTypes,
                               boolean includeWarnings,
                               boolean includeParseErrors,
                               String jsonOutputPath) {
        this.sourceRootPath = sourceRootPath;
        this.daoBaseTypes = daoBaseTypes;
        this.includeWarnings = includeWarnings;
        this.includeParseErrors = includeParseErrors;
        this.jsonOutputPath = jsonOutputPath;
    }

    public String getSourceRootPath() { return sourceRootPath; }
    public List<String> getDaoBaseTypes() { return daoBaseTypes; }
    public boolean isIncludeWarnings() { return includeWarnings; }
    public boolean isIncludeParseErrors() { return includeParseErrors; }
    public String getJsonOutputPath() { return jsonOutputPath; }

    public boolean hasDaoFilter() {
        return daoBaseTypes != null && !daoBaseTypes.isEmpty();
    }
}
