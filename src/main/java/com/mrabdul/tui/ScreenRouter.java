package com.mrabdul.tui;

import com.googlecode.lanterna.gui2.Panel;
import com.mrabdul.tools.ToolModule;
import com.mrabdul.tools.ToolScreen;

public class ScreenRouter {
    private ToolModule active;

    public void show(Panel contentHost, ToolModule module, StatusBar statusBar, TaskRunner taskRunner) {
        if (active != null) {
            ToolScreen oldScreen = active.screen();
            oldScreen.onHide(statusBar);
        }

        active = module;
        contentHost.removeAllComponents();

        ToolScreen newScreen = module.screen();
        contentHost.addComponent(newScreen.render(statusBar, taskRunner));
        newScreen.onShow(statusBar);

        contentHost.invalidate();
    }

    public ToolModule active() {
        return active;
    }
}
