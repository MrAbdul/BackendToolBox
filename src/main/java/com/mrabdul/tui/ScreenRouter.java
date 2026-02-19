package com.mrabdul.tui;

import com.googlecode.lanterna.gui2.Panel;
import com.mrabdul.tools.ToolModule;
import com.mrabdul.tools.ToolScreen;

public class ScreenRouter {
    private ToolModule activeModule;
    private ToolScreen activeScreen;

    public void show(Panel contentHost, ToolModule module, StatusBar statusBar, TaskRunner taskRunner) {
        if (activeScreen != null) {
            activeScreen.onHide(statusBar);
        }

        activeModule = module;
        activeScreen = module.screen();

        contentHost.removeAllComponents();
        contentHost.addComponent(activeScreen.render(statusBar, taskRunner));
        activeScreen.onShow(statusBar);

        contentHost.invalidate();
    }

    public ToolModule active() {
        return activeModule;
    }
}