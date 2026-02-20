package com.mrabdul.tools.cachettl;

import java.util.ArrayList;
import java.util.List;

public class CacheTtlInspectorJsonReport {
    public String tool = "cachettl";
    public String version = "0.1";

    public String sourceRoot;
    public String configPath;

    public int scannedFileCount;
    public int operationsCount;
    public int namespacesCount;

    public List<CacheOperation> operations = new ArrayList<CacheOperation>();
    public List<CacheNamespaceSummary> namespaces = new ArrayList<CacheNamespaceSummary>();
}