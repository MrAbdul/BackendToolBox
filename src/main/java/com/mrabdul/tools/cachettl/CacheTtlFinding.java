package com.mrabdul.tools.cachettl;

public class CacheTtlFinding {
    public String severity;   // HIGH/MED/LOW
    public String kind;       // NO_TTL_WITHOUT_DELETE / VERY_LONG_TTL / DYNAMIC_TTL / etc
    public String namespaceKey;
    public String cacheLayer;

    public String file;
    public int line;

    public String message;

    public CacheTtlFinding() {}

    public CacheTtlFinding(String severity, String kind, String namespaceKey, String cacheLayer,
                           String file, int line, String message) {
        this.severity = severity;
        this.kind = kind;
        this.namespaceKey = namespaceKey;
        this.cacheLayer = cacheLayer;
        this.file = file;
        this.line = line;
        this.message = message;
    }
}