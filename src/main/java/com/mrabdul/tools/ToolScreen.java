package com.mrabdul.tools;

import com.googlecode.lanterna.gui2.Component;
import com.mrabdul.tui.StatusBar;
import com.mrabdul.tui.TaskRunner;

public interface ToolScreen {
    Component render(StatusBar statusBar, TaskRunner taskRunner);

    void onShow(StatusBar statusBar);
    void onHide(StatusBar statusBar);
}
