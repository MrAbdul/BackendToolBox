package com.mrabdul.tools.lookupdiffer;

import com.googlecode.lanterna.gui2.*;
import com.mrabdul.tools.ToolScreen;
import com.mrabdul.tui.AutoCompleteTextBox;
import com.mrabdul.tui.StatusBar;
import com.mrabdul.tui.TaskRunner;
import com.mrabdul.tui.UiSizes;

import java.io.File;
import java.util.concurrent.Future;

import static com.mrabdul.tui.UiForms.*;

public class LookupDifferScreen implements ToolScreen {

    private final LookupDifferService service;
    private Future<?> runningTask;
    private TextBox outputBox;

    public LookupDifferScreen(LookupDifferService service) {
        this.service = service;
    }

    @Override
    public Component render(final StatusBar statusBar, final TaskRunner taskRunner) {
        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.addComponent(new Label("Lookup Differ (DDL + PK + INSERT exports)"));

        final AutoCompleteTextBox sourceDir = new AutoCompleteTextBox(UiSizes.INPUT_WIDE, 1);
        final AutoCompleteTextBox targetDir = new AutoCompleteTextBox(UiSizes.INPUT_WIDE, 1);
        final AutoCompleteTextBox outDir = new AutoCompleteTextBox(UiSizes.INPUT_WIDE, 1);
        final AutoCompleteTextBox jsonOut = new AutoCompleteTextBox(UiSizes.INPUT_WIDE, 1);

        String base = new File(".").getAbsoluteFile().getParent();
        sourceDir.setText(base);
        targetDir.setText(base);
        outDir.setText("");
        jsonOut.setText("");

        final TextBox tableContains = new TextBox(UiSizes.inputMed());
        tableContains.setText("");

        final CheckBox caseInsensitive = new CheckBox("Case-insensitive identifiers (recommended)");
        caseInsensitive.setChecked(true);

        Panel form = twoColumnForm();
        row(form, "Source dir (PROD export):", sourceDir);
        row(form, "Target dir (UAT export):", targetDir);
        row(form, "Out dir (optional):", outDir);
        row(form, "JSON out (optional):", jsonOut);
        row(form, "Table contains filter:", tableContains);
        span2(form, caseInsensitive);

        root.addComponent(form.withBorder(Borders.singleLine("Options")));

        Button runBtn = new Button("Run diff", new Runnable() {
            @Override public void run() {
                String s = safe(sourceDir.getText());
                String t = safe(targetDir.getText());

                if (s.isEmpty() || t.isEmpty()) {
                    statusBar.setError("sourceDir/targetDir is empty.");
                    return;
                }
                if (!new File(s).isDirectory()) {
                    statusBar.setError("Invalid sourceDir: " + s);
                    return;
                }
                if (!new File(t).isDirectory()) {
                    statusBar.setError("Invalid targetDir: " + t);
                    return;
                }

                LookupDifferRequest req = new LookupDifferRequest(
                        s,
                        t,
                        caseInsensitive.isChecked(),
                        safe(tableContains.getText()).isEmpty() ? null : safe(tableContains.getText()),
                        safe(outDir.getText()).isEmpty() ? null : safe(outDir.getText()),
                        safe(jsonOut.getText()).isEmpty() ? null : safe(jsonOut.getText())
                );

                if (runningTask != null) runningTask.cancel(true);

                statusBar.setInfo("Diffing folders...");
                if (outputBox != null) outputBox.setText("Running diff...\n");

                runningTask = taskRunner.submit(new Runnable() {
                    @Override public void run() {
                        try {
                            LookupDifferResult res = service.run(req);
                            if (outputBox != null) outputBox.setText(res.getReportText());

                            if (res.isOk()) {
                                statusBar.setInfo("Diff complete: OK (no diffs).");
                            } else {
                                statusBar.setWarn("Diff complete: tables=" + res.getMissingTables()
                                        + " cols=" + res.getMissingColumns()
                                        + " pks=" + res.getMissingPks()
                                        + " inserts=" + res.getMissingRows()
                                        + " updates=" + res.getMismatchedRows());
                            }
                        } catch (Exception e) {
                            if (outputBox != null) outputBox.setText("Diff failed:\n" + e.toString());
                            statusBar.setError("Diff failed: " + e.getMessage());
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
        statusBar.setInfo("Lookup Differ ready.");
    }

    @Override
    public void onHide(StatusBar statusBar) {
        if (runningTask != null) {
            runningTask.cancel(true);
            runningTask = null;
        }
        statusBar.setInfo("Leaving Lookup Differ.");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}