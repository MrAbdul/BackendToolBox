package com.mrabdul.tools.ssl;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.mrabdul.tools.ToolScreen;
import com.mrabdul.tui.AutoCompleteTextBox;
import com.mrabdul.tui.StatusBar;
import com.mrabdul.tui.TaskRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.Future;

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

        final AutoCompleteTextBox jksPath = new AutoCompleteTextBox(80,1);
        final TextBox jksPassword = new TextBox(new TerminalSize(40, 1));
        jksPassword.setMask('*');

        final TextBox url = new TextBox(new TerminalSize(80, 1));
        url.setText("https://example.com:443");

        final TextBox proxy = new TextBox(new TerminalSize(40, 1));
        proxy.setText(""); // e.g. 10.10.10.10:8080

        final CheckBox useAsTrustStore = new CheckBox("Use same JKS as TrustStore (recommended)");
        useAsTrustStore.setChecked(true);

        final CheckBox hostnameVerification = new CheckBox("Enable hostname verification");
        hostnameVerification.setChecked(true);

        final TextBox output = new TextBox(new TerminalSize(110, 20));
        output.setReadOnly(true);

        final Button[] runBtnHolder = new Button[1];

        runBtnHolder[0] = new Button("Run SSL Check", new Runnable() {
            @Override
            public void run() {

                final Button runBtn = runBtnHolder[0];

                final String p = jksPath.getText();
                final String pass = jksPassword.getText();
                final String u = url.getText();
                final String pr = proxy.getText();

                if (p == null || p.trim().isEmpty()) {
                    statusBar.setWarn("JKS path is required.");
                    return;
                }
                if (pass == null) {
                    statusBar.setWarn("JKS password is required (empty allowed but must be provided).");
                    return;
                }
                if (u == null || u.trim().isEmpty()) {
                    statusBar.setWarn("URL/host is required.");
                    return;
                }

                final ProxyConfig proxyCfg = ProxyConfig.parse(pr);
                final SslCheckRequest req = new SslCheckRequest(
                        p.trim(),
                        pass,
                        u.trim(),
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
                if (output != null) output.setText("");
                statusBar.setInfo("Output cleared.");
            }
        });
        final Button runBtn = runBtnHolder[0];

        Panel form = new Panel(new GridLayout(2).setHorizontalSpacing(1).setVerticalSpacing(1));

        form.addComponent(new Label("JKS path (Ctrl+Space or F2 to autocomplete):"));
        form.addComponent(jksPath);


        form.addComponent(new Label("JKS password: "));
        form.addComponent(jksPassword);

        form.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        form.addComponent(new Label("URL (https://host:port or host:port):"));
        form.addComponent(url);

        form.addComponent(new Label("Proxy (optional host:port): "));
        form.addComponent(proxy);
        root.addComponent(form.withBorder(Borders.singleLine("Options")));

        root.addComponent(useAsTrustStore);
        root.addComponent(hostnameVerification);

        Panel actions = new Panel(new LinearLayout(Direction.HORIZONTAL));
        actions.addComponent(runBtn);
        actions.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actions.addComponent(clearBtn);
        root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        output.setReadOnly(true);
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
}
