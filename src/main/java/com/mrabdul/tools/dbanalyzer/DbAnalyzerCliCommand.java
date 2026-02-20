package com.mrabdul.tools.dbanalyzer;

import com.mrabdul.cli.CliArgs;
import com.mrabdul.tools.CliCommand;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DbAnalyzerCliCommand implements CliCommand {

    private final DbAnalyzerService service;

    public DbAnalyzerCliCommand(DbAnalyzerService service) {
        this.service = service;
    }

    @Override
    public String id() {
        return "dbanalyzer";
    }

    @Override
    public String description() {
        return "Diff SQL embedded in two codebases (base vs target) and report schema-relevant changes.";
    }

    @Override
    public int run(String[] args) throws Exception {
        Map<String, String> a = CliArgs.parse(args);

        if (isHelpRequested(args, a)) {
            printHelp();
            return 0;
        }

        String baseRoot = CliArgs.get(a, "baseRoot", "");
        String targetRoot = CliArgs.get(a, "targetRoot", "");
        String includePackages = CliArgs.get(a, "includePackages", "");
        boolean includeDynamic = CliArgs.getBool(a, "includeDynamic", false);

        if (baseRoot == null || baseRoot.trim().isEmpty() || targetRoot == null || targetRoot.trim().isEmpty()) {
            System.err.println("ERROR: Missing required arguments.");
            System.err.println();
            printHelp();
            return 2;
        }

        DbAnalyzerRequest req = new DbAnalyzerRequest(
                baseRoot.trim(),
                targetRoot.trim(),
                includePackages == null ? "" : includePackages.trim(),
                includeDynamic
        );

        DbAnalyzerResult res = service.analyze(req);
        System.out.println(res.toReport());

        // Exit codes:
        // 0 = no schema-relevant SQL changes
        // 1 = changes detected
        return res.hasSchemaRelevantChanges() ? 1 : 0;
    }

    private boolean isHelpRequested(String[] rawArgs, Map<String, String> parsedArgs) {
        if (parsedArgs != null && (parsedArgs.containsKey("help") || parsedArgs.containsKey("h"))) {
            return true;
        }
        if (rawArgs == null) return false;
        for (String x : rawArgs) {
            if ("--help".equalsIgnoreCase(x) || "-h".equalsIgnoreCase(x)) return true;
        }
        return false;
    }

    private void printHelp() {
        System.out.println("Command: dbanalyzer");
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + description());
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar BackendToolBox.jar --toolbox.mode=cli dbanalyzer --baseRoot <path> --targetRoot <path> [options]");
        System.out.println();
        System.out.println("Required options:");
        System.out.println("  --baseRoot <path>                 Base codebase root (e.g., master)");
        System.out.println("  --targetRoot <path>               Target codebase root (e.g., migration branch)");
        System.out.println();
        System.out.println("Optional options:");
        System.out.println("  --includePackages <csv>           Comma-separated package prefixes filter");
        System.out.println("                                   Example: com.bbyn.dao,com.bbyn.repo");
        System.out.println("  --includeDynamic <true|false>     Include dynamic SQL fragments. Default: false");
        System.out.println("  --help, -h                        Show this help");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0  No schema-relevant SQL changes detected");
        System.out.println("  1  SQL changes detected");
        System.out.println("  2  Invalid usage / missing args");
    }
}