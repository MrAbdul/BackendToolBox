package com.mrabdul.tools.ssl;
import com.mrabdul.cli.CliArgs;
import com.mrabdul.tools.CliCommand;
import org.springframework.stereotype.Component;

import java.util.Map;
@Component
public class SslCheckCliCommand implements CliCommand {

    private final SslCheckerService service;

    public SslCheckCliCommand(SslCheckerService service) {
        this.service = service;
    }

    @Override
    public String id() {
        return "ssl-check";
    }

    @Override
    public String description() {
        return "Run an SSL/TLS handshake check using a JKS (optionally via proxy).";
    }

    @Override
    public int run(String[] args) {
        Map<String, String> a = CliArgs.parse(args);

        if (isHelpRequested(args, a)) {
            printHelp();
            return 0;
        }

        String jksPath = CliArgs.get(a, "jks", "");
        String jksPass = CliArgs.get(a, "pass", "");
        String target = CliArgs.get(a, "target", "");
        String proxyRaw = CliArgs.get(a, "proxy", "");
        boolean trustSame = CliArgs.getBool(a, "trustSame", true);
        boolean hostnameVerification = CliArgs.getBool(a, "hostnameVerification", true);

        if (jksPath.trim().isEmpty() || target.trim().isEmpty()) {
            System.err.println("ERROR: Missing required arguments.");
            System.err.println();
            printHelp();
            return 2;
        }

        ProxyConfig proxy = ProxyConfig.parse(proxyRaw);

        SslCheckRequest req = new SslCheckRequest(
                jksPath.trim(),
                jksPass == null ? "" : jksPass,
                target.trim(),
                proxy,
                trustSame,
                hostnameVerification
        );

        SslCheckResult res = service.check(req);
        System.out.println(res.getReport());
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
        System.out.println("Command: ssl-check");
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + description());
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar BackendToolBox.jar --toolbox.mode=cli ssl-check --jks <path> --pass <password> --target <https://host:port|host:port>");
        System.out.println();
        System.out.println("Required options:");
        System.out.println("  --jks <path>          Path to JKS file");
        System.out.println("  --target <target>     https://host:port OR host:port (defaults to 443 if port omitted by service)");
        System.out.println();
        System.out.println("Optional options:");
        System.out.println("  --pass <password>                 JKS password (can be empty). Default: \"\"");
        System.out.println("  --proxy <host:port>               HTTP CONNECT proxy (optional)");
        System.out.println("  --trustSame <true|false>          Use same JKS as TrustStore. Default: true");
        System.out.println("  --hostnameVerification <true|false>  Enable HTTPS hostname verification. Default: true");
        System.out.println("  --help, -h                        Show this help");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0  OK");
        System.out.println("  1  Check failed (handshake/validation failed)");
        System.out.println("  2  Invalid usage / missing args");
    }
}
