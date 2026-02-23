package com.mrabdul.tools.lookupdiffer;

public class DiffFinding {
    // TABLE_MISSING, COLUMN_MISSING, ROW_MISSING, ROW_MISMATCH, PARSE_ERROR, WARN_NO_PK
    public String kind;
    public String table;
    public String file;
    public int line;

    public String message;

    // Optional payloads
    public String ddl;        // e.g. ALTER TABLE...
    public String insertSql;  // e.g. INSERT INTO... or UPDATE...

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