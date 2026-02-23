package com.mrabdul.tools.lookupdiffer;

import java.util.*;

public class SqlExportIndex {

    // Table DDL (for columns diff)
    public final Map<String, TableDdl> ddlsByTableKey = new LinkedHashMap<String, TableDdl>();

    // PK columns per table (from *_PK.sql)
    public final Map<String, List<String>> pkColumnsByTableKey = new LinkedHashMap<String, List<String>>();

    // Rows parsed from INSERTs per table (we first store as list, then build pk-keyed maps)
    public final Map<String, List<Row>> rowsListByTableKey = new LinkedHashMap<String, List<Row>>();

    // Rows keyed by pk tuple per table (filled after indexing when PK known)
    public final Map<String, Map<String, Row>> rowsByPkKeyByTableKey = new LinkedHashMap<String, Map<String, Row>>();

    // Parse errors (best effort)
    public final List<DiffFinding> parseErrors = new ArrayList<DiffFinding>();

    public static class TableDdl {
        public final String tableName; // as found (may include schema/quotes trimmed)
        public final String file;
        public final int line;
        public final Map<String, ColumnDef> columnsByKey;

        public TableDdl(String tableName, String file, int line, Map<String, ColumnDef> columnsByKey) {
            this.tableName = tableName;
            this.file = file;
            this.line = line;
            this.columnsByKey = columnsByKey;
        }
    }

    public static class ColumnDef {
        public final String columnName;      // as found
        public final String columnSqlDef;    // entire column definition (no trailing comma)
        public final int line;

        public ColumnDef(String columnName, String columnSqlDef, int line) {
            this.columnName = columnName;
            this.columnSqlDef = columnSqlDef;
            this.line = line;
        }
    }

    public static class Row {
        public final String tableName;
        public final String file;
        public final int line;

        // columnKey -> value (SQL literal/expression, normalized)
        public final Map<String, String> colToVal;

        // store original insert for generating INSERT patch
        public final String originalInsertSql;

        public Row(String tableName, String file, int line, Map<String, String> colToVal, String originalInsertSql) {
            this.tableName = tableName;
            this.file = file;
            this.line = line;
            this.colToVal = colToVal;
            this.originalInsertSql = originalInsertSql;
        }
    }
}