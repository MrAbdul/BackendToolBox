package com.mrabdul.tui;

import com.mrabdul.tools.ToolModule;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import java.util.List;

@Component
@ConditionalOnProperty(name = "toolbox.mode", havingValue = "tui")
public class TuiRunner implements CommandLineRunner {

    private final List<ToolModule> toolModules;

    public TuiRunner(List<ToolModule> toolModules) {
        this.toolModules = toolModules;
    }

    @Override
    public void run(String... args) throws Exception {
        if (System.console() == null) {
            System.err.println("WARN: No interactive console detected (System.console() is null).");
            System.err.println("      If you're running from a service/CI or with redirected I/O, TUI may not work.");
        }

        Screen screen = null;
        try {
            screen = new DefaultTerminalFactory().createScreen();
            screen.startScreen();

            MainWindow window = new MainWindow(screen, toolModules);
            window.start();
        } catch (Throwable t) {
            System.err.println("Failed to start TUI (Lanterna).");
            System.err.println("Common fixes:");
            System.err.println("  - Use a real terminal/PTY (SSH with -t, MobaXterm normal SSH session).");
            System.err.println("  - Ensure TERM is set (e.g. export TERM=xterm-256color).");
            System.err.println("  - On Windows, prefer Windows Terminal over legacy CMD/PowerShell host.");
            System.err.println("Details: " + t.getClass().getName() + ": " + t.getMessage());
            throw t;
        } finally {
            if (screen != null) {
                try { screen.stopScreen(); } catch (Exception ignore) {}
            }
        }
    }
}