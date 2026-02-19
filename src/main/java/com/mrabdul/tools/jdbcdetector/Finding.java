package com.mrabdul.tools.jdbcdetector;

public class Finding {
    public String kind; // ISSUE, WARN, PARSE_ERROR
    public String file;
    public int line;
    public String method;
    public String resourceType;
    public String variable;
    public String message;

    public Finding() {}

    public Finding(String kind, String file, int line, String method,
                   String resourceType, String variable, String message) {
        this.kind = kind;
        this.file = file;
        this.line = line;
        this.method = method;
        this.resourceType = resourceType;
        this.variable = variable;
        this.message = message;
    }

    public static Finding parseError(String file, String msg) {
        return new Finding("PARSE_ERROR", file, -1, "", "", "", msg);
    }
}