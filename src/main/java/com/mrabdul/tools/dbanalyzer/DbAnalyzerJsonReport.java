package com.mrabdul.tools.dbanalyzer;

import java.util.*;

public class DbAnalyzerJsonReport {

    public String tool = "dbanalyzer";
    public String version = "0.1";
    public String baseRoot;
    public String targetRoot;
    public boolean includeDynamic;
    public List<String> includePackages = new ArrayList<String>();

    public int baseSqlCount;
    public int targetSqlCount;

    public int modifiedCount;
    public int addedCount;
    public int removedCount;

    public List<Change> changes = new ArrayList<Change>();

    public static class Change {
        public String kind;       // ADDED/REMOVED/MODIFIED
        public String key;        // pretty key for humans

        public Artifact base;     // may be null
        public Artifact target;   // may be null

        // Only meaningful for MODIFIED (can be empty otherwise)
        public Map<String, List<String>> newColumnsByTable = new LinkedHashMap<String, List<String>>();
    }

    public static class Artifact {
        public String idKey;          // stable key: relativeFile#class#owner
        public String file;
        public String className;
        public String owner;          // methodOrField
        public int line;
        public boolean dynamic;

        public String normalizedSql;  // store normalized (safer, consistent)
        public String type;           // SELECT/INSERT/UPDATE/DELETE/UNKNOWN
        public List<String> tables = new ArrayList<String>();
        public Map<String, List<String>> columnsByTable = new LinkedHashMap<String, List<String>>();
        public boolean parsedFully;
    }
}
