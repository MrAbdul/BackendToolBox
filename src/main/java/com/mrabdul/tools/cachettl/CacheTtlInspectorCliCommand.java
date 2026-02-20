package com.mrabdul.tools.cachettl;

import com.mrabdul.cli.CliArgs;
import com.mrabdul.tools.CliCommand;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

@Component
public class CacheTtlInspectorCliCommand implements CliCommand {

    private final CacheTtlInspectorService service;

    public CacheTtlInspectorCliCommand(CacheTtlInspectorService service) {
        this.service = service;
    }

    @Override
    public String id() {
        return "cachettl";
    }

    @Override
    public String description() {
        return "Scan a codebase for cache put/delete operations and TTL usage (config-driven).";
    }

    @Override
    public int run(String[] args) throws Exception {
        Map<String, String> a = CliArgs.parse(args);

        if (isHelpRequested(args, a)) {
            printHelp();
            return 0;
        }
        boolean dumpDefault = CliArgs.getBool(a, "dump-default-config", false);
        if (dumpDefault) {
            dumpDefaultConfigToStdout();
            return 0;
        }
        String sourceRoot = CliArgs.get(a, "sourceRoot", "");
        String configPath = CliArgs.get(a, "config", "");
        String jsonOut = CliArgs.get(a, "jsonOut", "");

        if (sourceRoot == null || sourceRoot.trim().isEmpty()) {
            System.err.println("ERROR: Missing required arguments.");
            System.err.println();
            printHelp();
            return 2;
        }

        CacheTtlInspectorRequest req = new CacheTtlInspectorRequest(
                sourceRoot.trim(),
                (configPath == null || configPath.trim().isEmpty()) ? null : configPath.trim(),
                (jsonOut == null || jsonOut.trim().isEmpty()) ? null : jsonOut.trim()
        );
        String configUsed = (configPath == null || configPath.trim().isEmpty())
                ? "(built-in default: cachettl-default-config.json)"
                : new java.io.File(configPath.trim()).getAbsolutePath();

        System.out.println("Using config: " + configUsed);
        CacheTtlInspectorResult res = service.run(req);

        System.out.println(res.toReportText());

        // Exit codes:
        // 0 = scan ok (no "high severity" findings)
        // 1 = scan ok but findings exist (we treat orphan/noTTLWithoutDelete as findings)
        // 2 = invalid usage / crash
        return res.getFindingsCount() > 0 ? 1 : 0;
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
    private void dumpDefaultConfigToStdout() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("cachettl-default-config.json");
        if (in == null) {
            System.err.println("ERROR: default config not found in classpath: cachettl-default-config.json");
            return;
        }
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                System.out.write(buf, 0, n);
            }
            System.out.println();
        } finally {
            try { in.close(); } catch (Exception ignore) {}
        }
    }
    private void printHelp() {
        System.out.println("Command: cachettl");
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + description());
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar BackendToolBox.jar --toolbox.mode=cli cachettl --sourceRoot <path> [options]");
        System.out.println();
        System.out.println("Required options:");
        System.out.println("  --sourceRoot <path>     Root directory to scan (walks *.java)");
        System.out.println();
        System.out.println("Optional options:");
        System.out.println("  --config <path>                Optional. If omitted, built-in default config is used");
        System.out.println("  --dump-default-config          Print the built-in default config JSON and exit");
        System.out.println("  --jsonOut <path>        Write JSON report to this path");
        System.out.println("  --help, -h              Show this help");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0  OK (no findings)");
        System.out.println("  1  Findings detected");
        System.out.println("  2  Invalid usage / missing args");
    }
}