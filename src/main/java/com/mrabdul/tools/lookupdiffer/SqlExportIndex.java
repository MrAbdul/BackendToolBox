package com.mrabdul.tools.lookupdiffer;

import java.util.*;

public class SqlExportIndex {

    // tableKey -> ddl info
    public final Map<String, TableDdl> ddlsByTableKey = new LinkedHashMap<String, TableDdl>();

    // tableKey -> inserts
    public final Map<String, List<InsertRow>> rowsByTableKey = new LinkedHashMap<String, List<InsertRow>>();

    // tableKey -> list of primary key columns
    public final Map<String, List<String>> pksByTableKey = new LinkedHashMap<String, List<String>>();

    // tableKey -> PK DDL (ALTER TABLE ... ADD PRIMARY KEY ... or CREATE UNIQUE INDEX ...)
    public final Map<String, String> pkDdlsByTableKey = new LinkedHashMap<String, String>();

    public static class TableDdl {
        public final String tableName;
        public final String file;
        public final int line;
        public final Map<String, ColumnDef> columnsByKey;
        public String fullSql; // Store the original CREATE TABLE sql

        public TableDdl(String tableName, String file, int line, Map<String, ColumnDef> columnsByKey) {
            this.tableName = tableName;
            this.file = file;
            this.line = line;
            this.columnsByKey = columnsByKey;
        }
    }

    public static class ColumnDef {
        public final String columnName;
        public final String columnSqlDef;
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

        public final List<String> columns;
        public final List<String> values;
        public final String normalizedKey;
        public final String originalSql;

        public InsertRow(String tableName, String file, int line, List<String> columns, List<String> values, String normalizedKey, String originalSql) {
            this.tableName = tableName;
            this.file = file;
            this.line = line;
            this.columns = columns;
            this.values = values;
            this.normalizedKey = normalizedKey;
            this.originalSql = originalSql;
        }

        public String getPkKey(List<String> pkCols, boolean caseInsensitive) {
            StringBuilder sb = new StringBuilder();
            for (String pkCol : pkCols) {
                String search = caseInsensitive ? pkCol.toUpperCase() : pkCol;
                int idx = -1;
                for (int i = 0; i < columns.size(); i++) {
                    String colCmp = caseInsensitive ? columns.get(i).toUpperCase() : columns.get(i);
                    if (colCmp.equals(search)) {
                        idx = i;
                        break;
                    }
                }
                sb.append(idx >= 0 ? values.get(idx) : "NULL").append("|");
            }
            return sb.toString();
        }
    }
}