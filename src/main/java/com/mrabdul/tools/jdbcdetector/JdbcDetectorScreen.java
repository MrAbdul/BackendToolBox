package com.mrabdul.tools.jdbcdetector;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.mrabdul.tools.ToolScreen;
import com.mrabdul.tui.AutoCompleteTextBox;
import com.mrabdul.tui.StatusBar;
import com.mrabdul.tui.TaskRunner;

import java.io.File;
import java.util.List;
import java.util.concurrent.Future;

public class JdbcDetectorScreen implements ToolScreen {

    private final JdbcDetectorService service;

    private Future<?> runningTask;

    // UI refs (so we can update after run)
    private TextBox outputBox;

    public JdbcDetectorScreen(JdbcDetectorService service) {
        this.service = service;
    }

    @Override
    public Component render(final StatusBar statusBar, final TaskRunner taskRunner) {
        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.addComponent(new Label("JDBC Detector (Static Analysis)"));

        // ---- Inputs ----
        Panel form = new Panel(new GridLayout(2).setHorizontalSpacing(1).setVerticalSpacing(1));

        // Source root (with Ctrl+Space / F2 autocomplete via AutoCompleteTextBox)
        form.addComponent(new Label("Source root path:"));
        final AutoCompleteTextBox sourceRootBox = new AutoCompleteTextBox(80, 1);
        sourceRootBox.setText(new File(".").getAbsoluteFile().getParent());
        form.addComponent(sourceRootBox);

        // DAO base types filter
        form.addComponent(new Label("DAO base types (comma-separated):"));
        final TextBox daoFilterBox = new TextBox(new TerminalSize(80, 1));
        daoFilterBox.setText(""); // empty = analyze everything
        form.addComponent(daoFilterBox);

        // JSON output path (optional)
        form.addComponent(new Label("JSON output path (optional):"));
        final AutoCompleteTextBox jsonOutBox = new AutoCompleteTextBox(80, 1);
        jsonOutBox.setText("");
        form.addComponent(jsonOutBox);

        // Include warnings
        form.addComponent(new Label("Include warnings:"));
        final CheckBox includeWarnings = new CheckBox();
        includeWarnings.setChecked(true);
        form.addComponent(includeWarnings);

        // Include parse errors
        form.addComponent(new Label("Include parse errors:"));
        final CheckBox includeParseErrors = new CheckBox();
        includeParseErrors.setChecked(true);
        form.addComponent(includeParseErrors);

        root.addComponent(form.withBorder(Borders.singleLine("Options")));

        // ---- Buttons ----
        Panel actions = new Panel(new LinearLayout(Direction.HORIZONTAL));

        Button runBtn = new Button("Run scan", new Runnable() {
            @Override
            public void run() {
                final String sourceRoot = safeTrim(sourceRootBox.getText());
                final String daoRaw = safeTrim(daoFilterBox.getText());
                final String jsonOut = safeTrim(jsonOutBox.getText());

                // Basic validation (fail fast)
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

                // cancel previous run (optional)
                if (runningTask != null) runningTask.cancel(true);

                statusBar.setInfo("Scanning: " + sourceRoot + (req.hasDaoFilter() ? " (DAO filter ON)" : " (no DAO filter)"));
                if (outputBox != null) {
                    outputBox.setText("Running scan...\n");
                }

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

                            if (outputBox != null) {
                                outputBox.setText(sb.toString());
                            }

                            if (result.isOk()) {
                                statusBar.setInfo("Scan complete: OK (0 issues).");
                            } else {
                                statusBar.setWarn("Scan complete: " + result.getIssueCount() + " issue(s) found.");
                            }
                        } catch (Exception e) {
                            if (outputBox != null) {
                                outputBox.setText("Scan failed:\n" + e.toString());
                            }
                            statusBar.setError("Scan failed: " + e.getMessage());
                        }
                    }
                });
            }
        });

        Button clearBtn = new Button("Clear output", new Runnable() {
            @Override
            public void run() {
                if (outputBox != null) outputBox.setText("");
                statusBar.setInfo("Output cleared.");
            }
        });

        actions.addComponent(runBtn);
        actions.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actions.addComponent(clearBtn);

        root.addComponent(actions);

        // ---- Output ----
        outputBox = new TextBox(new TerminalSize(110, 22), TextBox.Style.MULTI_LINE);
        outputBox.setReadOnly(true);
        root.addComponent(outputBox.withBorder(Borders.singleLine("Report")));

        // little UX hint
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
