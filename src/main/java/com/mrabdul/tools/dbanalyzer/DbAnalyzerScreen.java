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
        root.addComponent(new Label("DB analyzer that will compare two codebases and generate a JSON report of differences"));


        AutoCompleteTextBox baseRoot = new AutoCompleteTextBox(90, 1);
        AutoCompleteTextBox targetRoot = new AutoCompleteTextBox(90, 1);

        TextBox includePackages = new TextBox(new TerminalSize(90, 1));
        includePackages.setText(""); // optional: com.bbyn.dao,com.bbyn.repo

        AutoCompleteTextBox jsonOutPath = new AutoCompleteTextBox(90, 1);
        jsonOutPath.setText(""); // optional

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
                        includeDynamic.isChecked(),
                        jsonOutPath.getText() == null ? "" : jsonOutPath.getText().trim()
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
        Button clearBtn = new Button("Clear output", new Runnable() {
            @Override
            public void run() {
                if (output != null) output.setText("");
                statusBar.setInfo("Output cleared.");
            }
        });


        Button run = runHolder[0];
        Panel form = new Panel(new GridLayout(2).setHorizontalSpacing(1).setVerticalSpacing(1));
        form.addComponent(new Label("Base codebase root  (Ctrl+Space / F2):"));
        form.addComponent(baseRoot);
        form.addComponent(new Label("Target codebase root (migration branch) (Ctrl+Space / F2):"));
        form.addComponent(targetRoot);
        form.addComponent(new Label("Optional package filter (comma-separated). Example: com.bbyn.dao,com.bbyn.repo"));
        form.addComponent(includePackages);
        form.addComponent(new Label("Optional JSON output path:"));
        form.addComponent(jsonOutPath);
        form.addComponent(includeDynamic);
        root.addComponent(form.withBorder(Borders.singleLine("Options")));
        Panel actions = new Panel(new LinearLayout(Direction.HORIZONTAL));
        actions.addComponent(run);
        actions.addComponent(clearBtn);
        root.addComponent(actions);
        output.setReadOnly(true);
        root.addComponent(output.withBorder(Borders.singleLine("Report")));

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
