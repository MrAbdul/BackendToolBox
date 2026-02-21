package com.mrabdul.tools.ssl;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.mrabdul.tools.ToolScreen;
import com.mrabdul.tui.AutoCompleteTextBox;
import com.mrabdul.tui.StatusBar;
import com.mrabdul.tui.TaskRunner;
import org.springframework.stereotype.Component;

import static com.mrabdul.tui.UiForms.*;

@Component
public class SslCheckerScreen implements ToolScreen {

    private final SslCheckerService service;

    public SslCheckerScreen(SslCheckerService service) {
        this.service = service;
    }

    @Override
    public Panel render(final StatusBar statusBar, final TaskRunner taskRunner) {
        final Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.addComponent(new Label("SSL checker that will validate SSL certificates and report any issues"));

        final AutoCompleteTextBox jksPath = new AutoCompleteTextBox(80, 1);

        final TextBox jksPassword = new TextBox(new TerminalSize(40, 1));
        jksPassword.setMask('*');

        final TextBox url = new TextBox(new TerminalSize(80, 1));
        url.setText("https://example.com:443");

        final TextBox proxy = new TextBox(new TerminalSize(40, 1));
        proxy.setText("");

        final CheckBox useAsTrustStore = new CheckBox("Use same JKS as TrustStore (recommended)");
        useAsTrustStore.setChecked(true);

        final CheckBox hostnameVerification = new CheckBox("Enable hostname verification");
        hostnameVerification.setChecked(true);

        final TextBox output = new TextBox(new TerminalSize(110, 20), TextBox.Style.MULTI_LINE);
        output.setReadOnly(true);

        final Button[] runBtnHolder = new Button[1];

        Panel form = twoColumnForm();
        row(form, "JKS path (Ctrl+Space / F2):", jksPath);
        row(form, "JKS password:", jksPassword);
        row(form, "URL (https://host:port or host:port):", url);
        row(form, "Proxy (optional host:port):", proxy);
        span2(form, useAsTrustStore);
        span2(form, hostnameVerification);

        root.addComponent(form.withBorder(Borders.singleLine("Options")));

        runBtnHolder[0] = new Button("Run SSL Check", new Runnable() {
            @Override
            public void run() {
                final Button runBtn = runBtnHolder[0];

                final String p = safeTrim(jksPath.getText());
                final String pass = jksPassword.getText(); // password can be empty
                final String u = safeTrim(url.getText());
                final String pr = safeTrim(proxy.getText());

                if (p.isEmpty()) {
                    statusBar.setWarn("JKS path is required.");
                    return;
                }
                if (pass == null) {
                    statusBar.setWarn("JKS password must be provided (empty allowed).");
                    return;
                }
                if (u.isEmpty()) {
                    statusBar.setWarn("URL/host is required.");
                    return;
                }

                final ProxyConfig proxyCfg = ProxyConfig.parse(pr);

                final SslCheckRequest req = new SslCheckRequest(
                        p,
                        pass,
                        u,
                        proxyCfg,
                        useAsTrustStore.isChecked(),
                        hostnameVerification.isChecked()
                );

                runBtn.setEnabled(false);
                output.setText("");
                statusBar.setInfo("Running SSL check...");

                taskRunner.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SslCheckResult res = service.check(req);
                            output.setText(res.getReport());
                            statusBar.setInfo(res.isOk() ? "SSL check OK." : "SSL check FAILED.");
                        } catch (Exception e) {
                            output.setText("ERROR: " + e.getMessage());
                            statusBar.setError("SSL check crashed: " + e.getMessage());
                        } finally {
                            runBtn.setEnabled(true);
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
        actions.addComponent(runBtnHolder[0]);
        actions.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actions.addComponent(clearBtn);

        root.addComponent(actions);
        root.addComponent(output.withBorder(Borders.singleLine("Report")));
        return root;
    }

    @Override
    public void onShow(StatusBar statusBar) {
        statusBar.setInfo("SSL Checker ready.");
    }

    @Override
    public void onHide(StatusBar statusBar) {
        // no-op
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}