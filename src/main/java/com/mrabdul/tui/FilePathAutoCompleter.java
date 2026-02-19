package com.mrabdul.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FilePathAutoCompleter {

    public static void autocomplete(TextBox box) {
        String raw = box.getText();
        if (raw == null) return;

        String text = expandTilde(raw.trim());
        if (text.isEmpty()) return;

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

        List<File> matches = findMatches(dir, prefix);
        if (matches.isEmpty()) return;

        if (matches.size() == 1) {
            apply(box, matches.get(0));
            return;
        }

        showChooser(box, dir, prefix, matches);
    }

    private static List<File> findMatches(File dir, String prefix) {
        File[] files = dir.listFiles();
        if (files == null) return Collections.emptyList();

        List<File> matches = new ArrayList<File>();
        for (File f : files) {
            if (f.getName().startsWith(prefix)) {
                matches.add(f);
            }
        }

        // Directories first, then name
        Collections.sort(matches, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return matches;
    }

    private static void apply(TextBox box, File f) {
        String newText = f.getAbsolutePath() + (f.isDirectory() ? File.separator : "");
        box.setText(newText);
        box.setCaretPosition(newText.length());
        box.invalidate(); // force redraw
    }

    private static void showChooser(final TextBox box,
                                    final File dir,
                                    final String prefix,
                                    final List<File> matches) {

        TextGUI gui = box.getTextGUI();
        if (!(gui instanceof WindowBasedTextGUI)) {
            // Not attached yet; nothing we can do
            return;
        }

        final WindowBasedTextGUI wgui = (WindowBasedTextGUI) gui;

        final BasicWindow win = new BasicWindow("Autocomplete");
        win.setHints(Collections.singletonList(Window.Hint.CENTERED));

        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
        panel.addComponent(new Label("Dir: " + dir.getAbsolutePath()));
        panel.addComponent(new Label("Matches for: \"" + prefix + "\" (Enter to pick, Esc to cancel)"));

        final EscapableActionListBox actions = new EscapableActionListBox(90, Math.min(12, matches.size()));
        actions.setOnEscape(new Runnable() {
            @Override public void run() {
                win.close();
            }
        });

        // Limit displayed items to avoid a huge window; you can page later if you want
        int maxItems = Math.min(matches.size(), 50);
        for (int i = 0; i < maxItems; i++) {
            final File f = matches.get(i);
            final String display = f.getName() + (f.isDirectory() ? File.separator : "");
            actions.addItem(display, new Runnable() {
                @Override
                public void run() {
                    apply(box, f);
                    win.close();
                }
            });
        }

        if (matches.size() > maxItems) {
            actions.addItem("... (" + (matches.size() - maxItems) + " more not shown)", new Runnable() {
                @Override public void run() { /* no-op */ }
            });
        }



        panel.addComponent(actions.withBorder(Borders.singleLine()));

        Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL));
        buttons.addComponent(new Button("Cancel", new Runnable() {
            @Override public void run() { win.close(); }
        }));
        panel.addComponent(buttons);

        win.setComponent(panel);
        wgui.addWindowAndWait(win);
    }

    private static String expandTilde(String text) {
        if (text.equals("~")) {
            return System.getProperty("user.home");
        }
        if (text.startsWith("~" + File.separator)) {
            return System.getProperty("user.home") + text.substring(1);
        }
        return text;
    }
}
