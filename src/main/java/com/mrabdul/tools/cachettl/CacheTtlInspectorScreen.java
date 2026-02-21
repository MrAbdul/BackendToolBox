package com.mrabdul.tools.cachettl;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.mrabdul.tools.ToolScreen;
import com.mrabdul.tui.AutoCompleteTextBox;
import com.mrabdul.tui.StatusBar;
import com.mrabdul.tui.TaskRunner;
import org.springframework.stereotype.Component;

import java.io.File;

import static com.mrabdul.tui.UiForms.*;

@Component
public class CacheTtlInspectorScreen implements ToolScreen {

    private final CacheTtlInspectorService service;

    public CacheTtlInspectorScreen(CacheTtlInspectorService service) {
        this.service = service;
    }

    @Override
    public Panel render(final StatusBar statusBar, final TaskRunner taskRunner) {
        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.addComponent(new Label("Cache TTL Inspector (config-driven)"));

        final AutoCompleteTextBox sourceRoot = new AutoCompleteTextBox(90, 1);
        sourceRoot.setText(new File(".").getAbsoluteFile().getParent());

        final AutoCompleteTextBox configPath = new AutoCompleteTextBox(90, 1);
        configPath.setText("");

        final AutoCompleteTextBox jsonOutPath = new AutoCompleteTextBox(90, 1);
        jsonOutPath.setText("");

        Panel form = twoColumnForm();
        row(form, "Source root path:", sourceRoot);
        row(form, "Config path (JSON) (blank = built-in default):", configPath);
        row(form, "JSON output path (optional):", jsonOutPath);
        root.addComponent(form.withBorder(Borders.singleLine("Options")));

        final TextBox output = new TextBox(new TerminalSize(120, 24), TextBox.Style.MULTI_LINE);
        output.setReadOnly(true);

        final Button[] runHolder = new Button[1];

        runHolder[0] = new Button("Run scan", new Runnable() {
            @Override
            public void run() {
                String sr = trim(sourceRoot.getText());
                String cp = trim(configPath.getText());
                String jo = trim(jsonOutPath.getText());

                if (sr.isEmpty()) {
                    statusBar.setError("Source root is required.");
                    return;
                }
                if (!new File(sr).isDirectory()) {
                    statusBar.setError("Invalid source root: " + sr);
                    return;
                }

                String configUsed = "(built-in default)";
                if (!cp.isEmpty()) {
                    if (!new File(cp).isFile()) {
                        statusBar.setError("Invalid config file: " + cp);
                        return;
                    }
                    configUsed = cp;
                }

                CacheTtlInspectorRequest req = new CacheTtlInspectorRequest(
                        sr,
                        cp.isEmpty() ? null : cp,
                        jo.isEmpty() ? null : jo
                );

                output.setText("");
                runHolder[0].setEnabled(false);
                statusBar.setInfo("Scanning cache operations... config=" + configUsed);

                taskRunner.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            CacheTtlInspectorResult res = service.run(req);
                            output.setText(res.toReportText());
                            statusBar.setInfo(res.getFindingsCount() > 0
                                    ? ("Scan done: findings=" + res.getFindingsCount())
                                    : "Scan done: OK (no findings)");
                        } catch (Exception e) {
                            output.setText("ERROR: " + e.getMessage());
                            statusBar.setError("CacheTTL crashed: " + e.getMessage());
                        } finally {
                            runHolder[0].setEnabled(true);
                        }
                    }
                });
            }
        });

        Button clearBtn = new Button("Clear output", new Runnable() {
            @Override
            public void run() {
                output.setText("");
                statusBar.setInfo("Output cleared.");
            }
        });

        Panel actions = new Panel(new LinearLayout(Direction.HORIZONTAL));
        actions.addComponent(runHolder[0]);
        actions.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actions.addComponent(clearBtn);

        root.addComponent(actions);
        root.addComponent(output.withBorder(Borders.singleLine("Report")));
        root.addComponent(new Label("Tip: In path fields press Ctrl+Space or F2 for autocomplete."));

        return root;
    }

    @Override
    public void onShow(StatusBar statusBar) {
        statusBar.setInfo("Cache TTL Inspector ready.");
    }

    @Override
    public void onHide(StatusBar statusBar) {
        // no-op
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}