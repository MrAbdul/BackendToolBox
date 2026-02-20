package com.mrabdul.tools.jdbcdetector;

import com.mrabdul.cli.CliArgs;
import com.mrabdul.tools.CliCommand;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class JdbcDetectorCliCommand implements CliCommand {

    private final JdbcDetectorService service;

    public JdbcDetectorCliCommand(JdbcDetectorService service) {
        this.service = service;
    }

    @Override
    public String id() {
        return "jdbcdetector";
    }

    @Override
    public String description() {
        return "Static scan a source tree for JDBC resource leak patterns.";
    }

    @Override
    public int run(String[] args) throws Exception {
        Map<String, String> a = CliArgs.parse(args);

        if (isHelpRequested(args, a)) {
            printHelp();
            return 0;
        }

        String sourceRoot = CliArgs.get(a, "sourceRoot", "");
        String daoBaseTypesRaw = CliArgs.get(a, "daoBaseTypes", "");
        String jsonOut = CliArgs.get(a, "jsonOut", "");

        boolean includeWarnings = CliArgs.getBool(a, "includeWarnings", true);
        boolean includeParseErrors = CliArgs.getBool(a, "includeParseErrors", true);

        if (sourceRoot == null || sourceRoot.trim().isEmpty()) {
            System.err.println("ERROR: Missing required arguments.");
            System.err.println();
            printHelp();
            return 2;
        }

        List<String> daoTypes = DaoFilterParser.parseCommaSeparated(daoBaseTypesRaw);

        JdbcDetectorRequest req = new JdbcDetectorRequest(
                sourceRoot.trim(),
                daoTypes,
                includeWarnings,
                includeParseErrors,
                (jsonOut == null || jsonOut.trim().isEmpty()) ? null : jsonOut.trim()
        );

        JdbcDetectorResult res = service.run(req);

        String report = res.getReportText();
        if (report != null && !report.trim().isEmpty()) {
            System.out.println(report);
        } else {
            System.out.println("No report produced.");
            System.out.println("Issues: " + res.getIssueCount());
            System.out.println("Warnings: " + res.getWarnCount());
            System.out.println("Parse errors: " + res.getParseErrorCount());
        }

        return res.isOk() ? 0 : 1;
    }

    private boolean isHelpRequested(String[] rawArgs, Map<String, String> parsedArgs) {
        if (parsedArgs != null && (parsedArgs.containsKey("help") || parsedArgs.containsKey("h"))) {
            return true;
        }
        if (rawArgs == null) return false;
        for (int i = 0; i < rawArgs.length; i++) {
            String x = rawArgs[i];
            if ("--help".equalsIgnoreCase(x) || "-h".equalsIgnoreCase(x)) return true;
        }
        return false;
    }

    private void printHelp() {
        System.out.println("Command: jdbcdetector");
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + description());
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar BackendToolBox.jar --toolbox.mode=cli jdbcdetector --sourceRoot <path> [options]");
        System.out.println();
        System.out.println("Required options:");
        System.out.println("  --sourceRoot <path>              Root directory to scan (will walk *.java)");
        System.out.println();
        System.out.println("Optional options:");
        System.out.println("  --daoBaseTypes <A,B,com.x.Dao>   Only analyze classes extending any of these base types");
        System.out.println("  --jsonOut <path>                Write findings as JSON to this path");
        System.out.println("  --includeWarnings <true|false>  Default: true");
        System.out.println("  --includeParseErrors <true|false> Default: true");
        System.out.println("  --help, -h                      Show this help");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0  OK (0 issues)");
        System.out.println("  1  Issues found (or scan completed with issues)");
        System.out.println("  2  Invalid usage / missing args");
    }
}