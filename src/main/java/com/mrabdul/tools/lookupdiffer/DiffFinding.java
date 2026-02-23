package com.mrabdul.tools.lookupdiffer;

public class DiffFinding {
    public String kind;    // TABLE_MISSING, COLUMN_MISSING, ROW_MISSING, ROW_DIFFERENT, WARN_NO_PK, PARSE_ERROR
    public String table;
    public String file;
    public int line;

    public String message;

    // payloads
    public String ddl;       // e.g. ALTER/UPDATE
    public String insertSql; // missing row insert

    public DiffFinding() {}

    public DiffFinding(String kind, String table, String file, int line, String message) {
        this.kind = kind;
        this.table = table;
        this.file = file;
        this.line = line;
        this.message = message;
    }

    public static DiffFinding parseError(String file, String msg) {
        return new DiffFinding("PARSE_ERROR", "", file, -1, msg);
    }
}