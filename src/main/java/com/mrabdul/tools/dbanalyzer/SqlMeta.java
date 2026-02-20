package com.mrabdul.tools.dbanalyzer;

import java.util.*;

public class SqlMeta {

    public enum Type { SELECT, UPDATE, INSERT, DELETE, UNKNOWN }

    private final Type type;
    private final List<String> tables;
    private final Map<String, Set<String>> columnsByTable; // table -> columns
    private final boolean parsedFully;

    public SqlMeta(Type type, List<String> tables, Map<String, Set<String>> columnsByTable, boolean parsedFully) {
        this.type = type;
        this.tables = tables == null ? Collections.emptyList() : tables;
        this.columnsByTable = columnsByTable == null ? Collections.emptyMap() : columnsByTable;
        this.parsedFully = parsedFully;
    }

    public Type getType() { return type; }
    public List<String> getTables() { return tables; }
    public Map<String, Set<String>> getColumnsByTable() { return columnsByTable; }
    public boolean isParsedFully() { return parsedFully; }

    public static SqlMeta unknown() {
        return new SqlMeta(Type.UNKNOWN, Collections.emptyList(), Collections.emptyMap(), false);
    }
}
