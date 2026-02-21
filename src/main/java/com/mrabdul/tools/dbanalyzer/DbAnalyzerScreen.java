package com.mrabdul.tools.dbanalyzer;

import com.googlecode.lanterna.gui2.*;
import com.mrabdul.tools.ToolScreen;
import com.mrabdul.tui.AutoCompleteTextBox;
import com.mrabdul.tui.StatusBar;
import com.mrabdul.tui.TaskRunner;
import com.mrabdul.tui.UiSizes;
import org.springframework.stereotype.Component;

import static com.mrabdul.tui.UiForms.*;

@Component
public class DbAnalyzerScreen implements ToolScreen {

    private final DbAnalyzerService service;

    public DbAnalyzerScreen(DbAnalyzerService service) {
        this.service = service;
    }

    @Override
    public Panel render(final StatusBar statusBar, final TaskRunner taskRunner) {
        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.addComponent(new Label("DB analyzer that will compare two codebases and generate a JSON report of differences"));

        final AutoCompleteTextBox baseRoot = new AutoCompleteTextBox(UiSizes.INPUT_WIDE, 1);
        final AutoCompleteTextBox targetRoot = new AutoCompleteTextBox(UiSizes.INPUT_WIDE, 1);

        final TextBox includePackages = new TextBox(UiSizes.inputWide());
        includePackages.setText("");

        final AutoCompleteTextBox jsonOutPath = new AutoCompleteTextBox(UiSizes.INPUT_WIDE, 1);
        jsonOutPath.setText("");

        final CheckBox includeDynamic = new CheckBox("Include dynamic SQL fragments (partial statements)");
        includeDynamic.setChecked(false);

        final TextBox output = UiSizes.reportBox();

        Panel form = twoColumnForm();
        row(form, "Base codebase root (Ctrl+Space / F2):", baseRoot);
        row(form, "Target codebase root (migration branch) (Ctrl+Space / F2):", targetRoot);
        row(form, "Optional package filter (comma-separated). Example: com.bbyn.dao,com.bbyn.repo", includePackages);
        row(form, "Optional JSON output path:", jsonOutPath);
        span2(form, includeDynamic);
        root.addComponent(form.withBorder(Borders.singleLine("Options")));

        final Button[] runHolder = new Button[1];

        runHolder[0] = new Button("Run SQL Diff", new Runnable() {
            @Override
            public void run() {
                String b = safeTrim(baseRoot.getText());
                String t = safeTrim(targetRoot.getText());

                if (b.isEmpty()) { statusBar.setWarn("Base root path is required."); return; }
                if (t.isEmpty()) { statusBar.setWarn("Target root path is required."); return; }

                DbAnalyzerRequest req = new DbAnalyzerRequest(
                        b,
                        t,
                        safeTrim(includePackages.getText()),
                        includeDynamic.isChecked(),
                        safeTrim(jsonOutPath.getText())
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
            @Override public void run() {
                output.setText("");
                statusBar.setInfo("Output cleared.");
            }
        });

        root.addComponent(actionsRow(runHolder[0], clearBtn));
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

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}