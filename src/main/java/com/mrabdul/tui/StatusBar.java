package com.mrabdul.tui;
import com.googlecode.lanterna.gui2.Label;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;

public class StatusBar {
    private final Label label;
    private final Deque<String> lastMessages = new ArrayDeque<String>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");

    public StatusBar(Label label) {
        this.label = label;
        setInfo("Ready.");
    }

    public void setInfo(String message) {
        push("INFO", message);
    }

    public void setWarn(String message) {
        push("WARN", message);
    }

    public void setError(String message) {
        push("ERROR", message);
    }

    private void push(String level, String message) {
        String ts = fmt.format(new Date());
        String full = ts + " [" + level + "] " + message;

        lastMessages.addFirst(full);
        while (lastMessages.size() > 30) {
            lastMessages.removeLast();
        }

        label.setText(full);
    }

    public String historyText() {
        StringBuilder sb = new StringBuilder();
        for (String msg : lastMessages) {
            sb.append(msg).append("\n");
        }
        return sb.toString();
    }
}
