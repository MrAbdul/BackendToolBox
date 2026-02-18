package com.mrabdul.tui;

import com.mrabdul.tools.ToolModule;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import java.util.List;

@Component
public class TuiRunner implements CommandLineRunner {

    private final List<ToolModule> toolModules;

    public TuiRunner(List<ToolModule> toolModules) {
        this.toolModules = toolModules;
    }

    @Override
    public void run(String... args) throws Exception {
        Screen screen = new DefaultTerminalFactory().createScreen();
        screen.startScreen();

        try {
            MainWindow window = new MainWindow(screen, toolModules);
            window.start();
        } finally {
            screen.stopScreen();
        }
    }
}
