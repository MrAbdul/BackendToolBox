package com.mrabdul.tools.hello;

import com.mrabdul.tools.ToolScreen;
import com.mrabdul.tui.StatusBar;
import com.mrabdul.tui.TaskRunner;
import com.googlecode.lanterna.gui2.*;
import org.springframework.stereotype.Component;

@Component
public class HelloScreen implements ToolScreen {

    public Panel render(StatusBar statusBar, TaskRunner taskRunner) {
        Panel panel = new Panel(new LinearLayout());

        panel.addComponent(new Label("Hello Abdul"));
        panel.addComponent(new EmptySpace());
        panel.addComponent(new Label("Your TUI toolbox is alive."));

        return panel;
    }

    public void onShow(StatusBar statusBar) {
        statusBar.setInfo("Hello Tool loaded.");
    }

    public void onHide(StatusBar statusBar) {
    }
}