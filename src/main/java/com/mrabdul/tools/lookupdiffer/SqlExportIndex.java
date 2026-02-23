package com.mrabdul.tools.lookupdiffer;

import java.util.*;

public class SqlExportIndex {

    // tableKey -> ddl info
    public final Map<String, TableDdl> ddlsByTableKey = new LinkedHashMap<String, TableDdl>();

    // tableKey -> inserts
    public final Map<String, List<InsertRow>> rowsByTableKey = new LinkedHashMap<String, List<InsertRow>>();

    public static class TableDdl {
        public final String tableName; // as found
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
        public final String columnSqlDef;    // "COL_NAME VARCHAR2(10) DEFAULT 'X' NOT NULL" (without trailing comma)
        public final int line;

        public ColumnDef(String columnName, String columnSqlDef, int line) {
            this.columnName = columnName;
            this.columnSqlDef = columnSqlDef;
            this.line = line;
        }
    }

    public static class InsertRow {
        public final String tableName;
        public final String file;
        public final int line;

        public final List<String> columns; // normalized column tokens
        public final List<String> values;  // normalized value tokens
        public final String normalizedKey; // stable identity

        public final String originalSql;   // raw insert statement line(s) flattened

        public InsertRow(String tableName,
                         String file,
                         int line,
                         List<String> columns,
                         List<String> values,
                         String normalizedKey,
                         String originalSql) {
            this.tableName = tableName;
            this.file = file;
            this.line = line;
            this.columns = columns;
            this.values = values;
            this.normalizedKey = normalizedKey;
            this.originalSql = originalSql;
        }
    }
}