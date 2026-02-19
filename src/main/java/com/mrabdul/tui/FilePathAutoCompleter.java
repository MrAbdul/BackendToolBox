package com.mrabdul.tui;

import com.googlecode.lanterna.gui2.TextBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FilePathAutoCompleter {

    public static void autocomplete(TextBox box) {
        String text = box.getText();
        if (text == null) return;

        text = text.trim();
        if (text.isEmpty()) return;

        // Support "~" in WSL/Linux
        if (text.startsWith("~" + File.separator) || text.equals("~")) {
            String home = System.getProperty("user.home");
            text = home + text.substring(1);
        }

        File current = new File(text);

        File dir;
        String prefix;

        if (current.isDirectory()) {
            dir = current;
            prefix = "";
        } else {
            dir = current.getParentFile();
            prefix = current.getName();
        }

        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        List<File> matches = new ArrayList<File>();
        for (File f : files) {
            if (f.getName().startsWith(prefix)) {
                matches.add(f);
            }
        }

        if (matches.size() == 1) {
            File f = matches.get(0);
            String newText = f.getAbsolutePath() + (f.isDirectory() ? File.separator : "");
            box.setText(newText);
            box.setCaretPosition(newText.length());
        }
        // If multiple matches: do nothing for now (we can enhance to show a popup list)
    }
}
