package com.mrabdul.tools.lookupdiffer;

import com.mrabdul.cli.CliArgs;
import com.mrabdul.tools.CliCommand;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LookupDifferCliCommand implements CliCommand {

    private final LookupDifferService service;

    public LookupDifferCliCommand(LookupDifferService service) {
        this.service = service;
    }

    @Override
    public String id() {
        return "lookupdiffer";
    }

    @Override
    public String description() {
        return "Diff exported lookup-table SQL folders (DDL + INSERTs) and generate schema/data patches.";
    }

    @Override
    public int run(String[] args) throws Exception {
        Map<String, String> a = CliArgs.parse(args);

        if (isHelpRequested(args, a)) {
            printHelp();
            return 0;
        }

        String sourceDir = CliArgs.get(a, "sourceDir", "");
        String targetDir = CliArgs.get(a, "targetDir", "");
        String outDir = CliArgs.get(a, "outDir", "");
        String jsonOut = CliArgs.get(a, "jsonOut", "");
        String tableContains = CliArgs.get(a, "tableContains", "");

        boolean caseInsensitive = CliArgs.getBool(a, "caseInsensitive", true);

        if (sourceDir.trim().isEmpty() || targetDir.trim().isEmpty()) {
            System.err.println("ERROR: Missing required args: --sourceDir and --targetDir");
            System.err.println();
            printHelp();
            return 2;
        }

        LookupDifferRequest req = new LookupDifferRequest(
                sourceDir.trim(),
                targetDir.trim(),
                caseInsensitive,
                tableContains.trim().isEmpty() ? null : tableContains.trim(),
                outDir.trim().isEmpty() ? null : outDir.trim(),
                jsonOut.trim().isEmpty() ? null : jsonOut.trim()
        );

        LookupDifferResult res = service.run(req);

        if (res.getReportText() != null && !res.getReportText().trim().isEmpty()) {
            System.out.println(res.getReportText());
        }

        return res.isOk() ? 0 : 1;
    }

    private boolean isHelpRequested(String[] rawArgs, Map<String, String> parsedArgs) {
        if (parsedArgs != null && (parsedArgs.containsKey("help") || parsedArgs.containsKey("h"))) {
            return true;
        }
        if (rawArgs == null) return false;
        for (String x : rawArgs) {
            if (x == null) continue;
            if ("--help".equalsIgnoreCase(x) || "-h".equalsIgnoreCase(x)) return true;
        }
        return false;
    }

    private void printHelp() {
        System.out.println("Command: lookupdiffer");
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + description());
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar BackendToolBox.jar --toolbox.mode=cli lookupdiffer --sourceDir <path> --targetDir <path> [options]");
        System.out.println();
        System.out.println("Required:");
        System.out.println("  --sourceDir <path>        Folder containing SOURCE SQL exports (e.g., PROD)");
        System.out.println("  --targetDir <path>        Folder containing TARGET SQL exports (e.g., UAT)");
        System.out.println();
        System.out.println("Optional:");
        System.out.println("  --outDir <path>           Write patch files: schema_patch.sql, data_patch.sql, missing_tables.sql");
        System.out.println("  --jsonOut <path>          Write findings as JSON");
        System.out.println("  --tableContains <text>    Only consider tables whose name contains this text");
        System.out.println("  --caseInsensitive <true|false>  Default: true");
        System.out.println("  --help, -h");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0  OK (no diffs found)");
        System.out.println("  1  Diffs found (missing tables/cols/rows) or parse errors");
        System.out.println("  2  Invalid usage / missing args");
    }
}