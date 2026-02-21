package com.mrabdul.tools.jdbcdetector;

import com.googlecode.lanterna.gui2.*;
import com.mrabdul.tools.ToolScreen;
import com.mrabdul.tui.AutoCompleteTextBox;
import com.mrabdul.tui.StatusBar;
import com.mrabdul.tui.TaskRunner;
import com.mrabdul.tui.UiSizes;

import java.io.File;
import java.util.List;
import java.util.concurrent.Future;

import static com.mrabdul.tui.UiForms.*;

public class JdbcDetectorScreen implements ToolScreen {

    private final JdbcDetectorService service;
    private Future<?> runningTask;
    private TextBox outputBox;

    public JdbcDetectorScreen(JdbcDetectorService service) {
        this.service = service;
    }

    @Override
    public Component render(final StatusBar statusBar, final TaskRunner taskRunner) {
        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.addComponent(new Label("JDBC Detector (Static Analysis)"));

        final AutoCompleteTextBox sourceRootBox = new AutoCompleteTextBox(UiSizes.INPUT_WIDE, 1);
        sourceRootBox.setText(new File(".").getAbsoluteFile().getParent());

        final TextBox daoFilterBox = new TextBox(UiSizes.inputWide());
        daoFilterBox.setText("");

        final AutoCompleteTextBox jsonOutBox = new AutoCompleteTextBox(UiSizes.INPUT_WIDE, 1);
        jsonOutBox.setText("");

        final CheckBox includeWarnings = new CheckBox("Include warnings");
        includeWarnings.setChecked(true);

        final CheckBox includeParseErrors = new CheckBox("Include parse errors");
        includeParseErrors.setChecked(true);

        Panel form = twoColumnForm();
        row(form, "Source root path:", sourceRootBox);
        row(form, "DAO base types (comma-separated):", daoFilterBox);
        row(form, "JSON output path (optional):", jsonOutBox);
        span2(form, includeWarnings);
        span2(form, includeParseErrors);
        root.addComponent(form.withBorder(Borders.singleLine("Options")));

        Button runBtn = new Button("Run scan", new Runnable() {
            @Override
            public void run() {
                final String sourceRoot = safeTrim(sourceRootBox.getText());
                final String daoRaw = safeTrim(daoFilterBox.getText());
                final String jsonOut = safeTrim(jsonOutBox.getText());

                if (sourceRoot.isEmpty()) {
                    statusBar.setError("Source root path is empty.");
                    return;
                }

                File rootDir = new File(sourceRoot);
                if (!rootDir.exists() || !rootDir.isDirectory()) {
                    statusBar.setError("Invalid source root (not a directory): " + sourceRoot);
                    return;
                }

                final List<String> daoTypes = DaoFilterParser.parseCommaSeparated(daoRaw);

                final JdbcDetectorRequest req = new JdbcDetectorRequest(
                        sourceRoot,
                        daoTypes,
                        includeWarnings.isChecked(),
                        includeParseErrors.isChecked(),
                        jsonOut.isEmpty() ? null : jsonOut
                );

                if (runningTask != null) runningTask.cancel(true);

                statusBar.setInfo("Scanning: " + sourceRoot + (req.hasDaoFilter() ? " (DAO filter ON)" : " (no DAO filter)"));
                if (outputBox != null) outputBox.setText("Running scan...\n");

                runningTask = taskRunner.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JdbcDetectorResult result = service.run(req);

                            StringBuilder sb = new StringBuilder();
                            sb.append("Done.\n")
                                    .append("Issues: ").append(result.getIssueCount()).append("\n")
                                    .append("Warnings: ").append(result.getWarnCount()).append("\n")
                                    .append("Parse errors: ").append(result.getParseErrorCount()).append("\n\n")
                                    .append(result.getReportText() == null ? "" : result.getReportText());

                            if (outputBox != null) outputBox.setText(sb.toString());

                            if (result.isOk()) statusBar.setInfo("Scan complete: OK (0 issues).");
                            else statusBar.setWarn("Scan complete: " + result.getIssueCount() + " issue(s) found.");
                        } catch (Exception e) {
                            if (outputBox != null) outputBox.setText("Scan failed:\n" + e.toString());
                            statusBar.setError("Scan failed: " + e.getMessage());
                        }
                    }
                });
            }
        });

        Button clearBtn = new Button("Clear output", new Runnable() {
            @Override public void run() {
                if (outputBox != null) outputBox.setText("");
                statusBar.setInfo("Output cleared.");
            }
        });

        root.addComponent(actionsRow(runBtn, clearBtn));

        outputBox = UiSizes.reportBox();
        root.addComponent(outputBox.withBorder(Borders.singleLine("Report")));
        root.addComponent(new Label("Tip: In path fields press Ctrl+Space or F2 for autocomplete."));
        return root;
    }

    @Override
    public void onShow(StatusBar statusBar) {
        statusBar.setInfo("JDBC Detector ready.");
    }

    @Override
    public void onHide(StatusBar statusBar) {
        if (runningTask != null) {
            runningTask.cancel(true);
            runningTask = null;
        }
        statusBar.setInfo("Leaving JDBC Detector.");
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}