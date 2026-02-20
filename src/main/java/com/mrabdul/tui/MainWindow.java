package com.mrabdul.tui;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.mrabdul.tools.ToolModule;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainWindow {

    private final Screen screen;
    private final List<ToolModule> tools;

    public MainWindow(Screen screen, List<ToolModule> tools) {
        this.screen = screen;
        this.tools = tools;
    }

    public void start() {
        final MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        gui.setTheme(new OpsTheme());
        final BasicWindow window = new BasicWindow("Ops Toolbox (TUI) - Java 8");

        final Label statusLabel = new Label("");
        final StatusBar statusBar = new StatusBar(statusLabel);
        final TaskRunner taskRunner = new TaskRunner();
        final ScreenRouter router = new ScreenRouter();

        Panel root = new Panel(new BorderLayout());

        // Header
        Panel header = new Panel(new BorderLayout());
        header.addComponent(new Label("F1: Logs"), BorderLayout.Location.LEFT);
        header.addComponent(new Label("F10: Exit"), BorderLayout.Location.RIGHT);

        // Left menu
        final ActionListBox menu = new ActionListBox(new TerminalSize(28, 25));
        menu.setPreferredSize(new TerminalSize(28, 25));

        // Center content
        final Panel contentHost = new Panel(new LinearLayout(Direction.VERTICAL));

        // Fill menu from modules
        for (final ToolModule tool : tools) {
            menu.addItem(tool.name(), new Runnable() {
                @Override
                public void run() {
                    statusBar.setInfo("Opening: " + tool.name());
                    router.show(contentHost, tool, statusBar, taskRunner);
                }
            });
        }

        Panel main = new Panel(new BorderLayout());
        main.addComponent(header, BorderLayout.Location.TOP);
        main.addComponent(menu.withBorder(Borders.singleLine("Tools")), BorderLayout.Location.LEFT);
        main.addComponent(contentHost.withBorder(Borders.singleLine("Workspace")), BorderLayout.Location.CENTER);

        // Status bar bottom
        Panel statusPanel = new Panel(new BorderLayout());
        statusPanel.addComponent(statusLabel, BorderLayout.Location.CENTER);

        root.addComponent(main, BorderLayout.Location.CENTER);
        root.addComponent(statusPanel.withBorder(Borders.singleLine()), BorderLayout.Location.BOTTOM);

        window.setComponent(root);

        // Key bindings
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
                KeyType kt = keyStroke.getKeyType();
                if (kt == KeyType.F1) {
                    UiDialogs.info(gui, "Recent Logs", statusBar.historyText());
                    deliverEvent.set(false);
                } else if (kt == KeyType.F10) {
                    basePane.close();
                    deliverEvent.set(false);
                }
            }
        });

        // default tool
        if (tools != null && !tools.isEmpty()) {
            router.show(contentHost, tools.get(0), statusBar, taskRunner);
        } else {
            contentHost.addComponent(new Label("No tools registered."));
        }

        gui.addWindowAndWait(window);

        taskRunner.shutdown();
    }
}
