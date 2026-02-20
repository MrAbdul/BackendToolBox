package com.mrabdul.cli;

import com.mrabdul.tools.CliCommand;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "toolbox.mode", havingValue = "cli", matchIfMissing = true)
public class CliRunner implements ApplicationRunner {

    private final Map<String, CliCommand> commands = new LinkedHashMap<String, CliCommand>();

    public CliRunner(List<CliCommand> commands) {
        if (commands != null) {
            for (CliCommand c : commands) {
                this.commands.put(c.id(), c);
            }
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        int exit = 0;
        try {
            exit = dispatch(args);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            exit = 2;
        }
        System.exit(exit);
    }

    private int dispatch(ApplicationArguments appArgs) throws Exception {
        String[] sourceArgs = appArgs.getSourceArgs(); // <-- includes everything as typed

        if (sourceArgs == null || sourceArgs.length == 0) {
            printHelp();
            return 0;
        }

        int cmdIndex = findCommandIndex(sourceArgs);
        if (cmdIndex < 0) {
            if (containsHelpToken(sourceArgs)) {
                printHelp();
                return 0;
            }
            System.err.println("No command specified.");
            System.err.println();
            printHelp();
            return 2;
        }

        String cmd = sourceArgs[cmdIndex];

        if ("help".equalsIgnoreCase(cmd) || "--help".equalsIgnoreCase(cmd) || "-h".equalsIgnoreCase(cmd)) {
            printHelp();
            return 0;
        }

        CliCommand command = commands.get(cmd);
        if (command == null) {
            System.err.println("Unknown command: " + cmd);
            System.err.println();
            printHelp();
            return 2;
        }

        String[] tail = Arrays.copyOfRange(sourceArgs, cmdIndex + 1, sourceArgs.length);
        return command.run(tail);
    }

    private int findCommandIndex(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (token == null) continue;
            if (commands.containsKey(token)) return i;
        }
        return -1;
    }

    private boolean containsHelpToken(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (token == null) continue;
            if ("help".equalsIgnoreCase(token) || "--help".equalsIgnoreCase(token) || "-h".equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private void printHelp() {
        System.out.println("BackendToolBox (CLI)");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar BackendToolBox.jar --toolbox.mode=cli <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        if (commands.isEmpty()) {
            System.out.println("  (none registered)");
        } else {
            for (CliCommand c : commands.values()) {
                System.out.println("  " + c.id() + "  - " + c.description());
            }
        }
        System.out.println();
        System.out.println("Other:");
        System.out.println("  help");
    }
}