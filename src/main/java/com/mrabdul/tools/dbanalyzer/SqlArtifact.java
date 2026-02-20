package com.mrabdul.tools.dbanalyzer;

public class SqlArtifact {
    private final String key;
    private final String relativeFile;
    private final String className;
    private final String methodOrField;
    private final int line;
    private final String rawSql;
    private final String normalizedSql;
    private final boolean dynamic;
    private final SqlMeta meta;

    public SqlArtifact(String key,
                       String relativeFile,
                       String className,
                       String methodOrField,
                       int line,
                       String rawSql,
                       String normalizedSql,
                       boolean dynamic,
                       SqlMeta meta) {
        this.key = key;
        this.relativeFile = relativeFile;
        this.className = className;
        this.methodOrField = methodOrField;
        this.line = line;
        this.rawSql = rawSql;
        this.normalizedSql = normalizedSql;
        this.dynamic = dynamic;
        this.meta = meta;
    }

    public String getKey() { return key; }
    public String getRelativeFile() { return relativeFile; }
    public String getClassName() { return className; }
    public String getMethodOrField() { return methodOrField; }
    public int getLine() { return line; }
    public String getRawSql() { return rawSql; }
    public String getNormalizedSql() { return normalizedSql; }
    public boolean isDynamic() { return dynamic; }
    public SqlMeta getMeta() { return meta; }
}
