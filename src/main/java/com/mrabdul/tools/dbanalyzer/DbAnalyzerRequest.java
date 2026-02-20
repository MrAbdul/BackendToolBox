package com.mrabdul.tools.dbanalyzer;

import java.util.*;
import java.util.stream.Collectors;

public class DbAnalyzerRequest {
    private final String baseRoot;
    private final String targetRoot;
    private final List<String> includePackages;
    private final boolean includeDynamic;

    public DbAnalyzerRequest(String baseRoot, String targetRoot, String includePackagesCsv, boolean includeDynamic) {
        this.baseRoot = baseRoot;
        this.targetRoot = targetRoot;
        this.includePackages = parseCsv(includePackagesCsv);
        this.includeDynamic = includeDynamic;
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public String getBaseRoot() { return baseRoot; }
    public String getTargetRoot() { return targetRoot; }
    public List<String> getIncludePackages() { return includePackages; }
    public boolean isIncludeDynamic() { return includeDynamic; }
}
