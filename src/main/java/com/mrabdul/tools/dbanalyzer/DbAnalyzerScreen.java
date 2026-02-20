package com.mrabdul.tools.dbanalyzer;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.mrabdul.tools.ToolScreen;
import com.mrabdul.tui.AutoCompleteTextBox;
import com.mrabdul.tui.StatusBar;
import com.mrabdul.tui.TaskRunner;
import org.springframework.stereotype.Component;

@Component
public class DbAnalyzerScreen implements ToolScreen {

    private final DbAnalyzerService service;

    public DbAnalyzerScreen(DbAnalyzerService service) {
        this.service = service;
    }

    @Override
    public Panel render(StatusBar statusBar, TaskRunner taskRunner) {
        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));

        AutoCompleteTextBox baseRoot = new AutoCompleteTextBox(90, 1);
        AutoCompleteTextBox targetRoot = new AutoCompleteTextBox(90, 1);

        TextBox includePackages = new TextBox(new TerminalSize(90, 1));
        includePackages.setText(""); // optional: com.bbyn.dao,com.bbyn.repo

        CheckBox includeDynamic = new CheckBox("Include dynamic SQL fragments (partial statements)");
        includeDynamic.setChecked(false);

        TextBox output = new TextBox(new TerminalSize(120, 24));
        output.setReadOnly(true);

        final Button[] runHolder = new Button[1];

        runHolder[0] = new Button("Run SQL Diff", new Runnable() {
            @Override
            public void run() {

                String b = baseRoot.getText() == null ? "" : baseRoot.getText().trim();
                String t = targetRoot.getText() == null ? "" : targetRoot.getText().trim();

                if (b.isEmpty()) {
                    statusBar.setWarn("Base root path is required.");
                    return;
                }
                if (t.isEmpty()) {
                    statusBar.setWarn("Target root path is required.");
                    return;
                }

                DbAnalyzerRequest req = new DbAnalyzerRequest(
                        b,
                        t,
                        includePackages.getText() == null ? "" : includePackages.getText().trim(),
                        includeDynamic.isChecked()
                );

                output.setText("");
                runHolder[0].setEnabled(false);
                statusBar.setInfo("Scanning codebases and diffing SQL...");

                taskRunner.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            DbAnalyzerResult res = service.analyze(req);
                            output.setText(res.toReport());
                            statusBar.setInfo(res.hasSchemaRelevantChanges()
                                    ? "SQL changes detected."
                                    : "No SQL changes detected.");
                        } catch (Exception e) {
                            output.setText("ERROR: " + e.getMessage());
                            statusBar.setError("DBAnalyzer crashed: " + e.getMessage());
                        } finally {
                            runHolder[0].setEnabled(true);
                        }
                    }
                });
            }
        });

        Button run = runHolder[0];

        root.addComponent(new Label("Base codebase root (master) (Ctrl+Space / F2):"));
        root.addComponent(baseRoot);

        root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        root.addComponent(new Label("Target codebase root (migration branch) (Ctrl+Space / F2):"));
        root.addComponent(targetRoot);

        root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        root.addComponent(new Label("Optional package filter (comma-separated). Example: com.bbyn.dao,com.bbyn.repo"));
        root.addComponent(includePackages);

        root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        root.addComponent(includeDynamic);

        root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        root.addComponent(run);

        root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        root.addComponent(new Label("Output:"));
        root.addComponent(output.withBorder(Borders.singleLine()));

        return root;
    }

    @Override
    public void onShow(StatusBar statusBar) {
        statusBar.setInfo("DBAnalyzer ready: compare SQL embedded in two codebases.");
    }

    @Override
    public void onHide(StatusBar statusBar) {
        // no-op
    }
}
