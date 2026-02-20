package com.mrabdul.tools.cachettl;

public class CacheTtlInspectorRequest {
    private final String sourceRoot;
    private final String configPath;   // optional (null/empty => use default)
    private final String jsonOutPath;  // optional

    public CacheTtlInspectorRequest(String sourceRoot, String configPath, String jsonOutPath) {
        this.sourceRoot = sourceRoot;
        this.configPath = configPath; // keep as-is, can be null
        this.jsonOutPath = jsonOutPath;
    }

    public String getSourceRoot() { return sourceRoot; }
    public String getConfigPath() { return configPath; }
    public String getJsonOutPath() { return jsonOutPath; }

    public boolean hasConfigPath() {
        return configPath != null && !configPath.trim().isEmpty();
    }
}