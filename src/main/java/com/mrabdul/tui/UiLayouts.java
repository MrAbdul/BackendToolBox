package com.mrabdul.tui;

import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Component;

public final class UiLayouts {
    private UiLayouts() {}

    /** Right-aligned label (col 0) */
    public static Label formLabel(String text) {
        Label l = new Label(text);

        // Align label to the RIGHT within its cell so all colons line up nicely
        l.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.END,
                GridLayout.Alignment.CENTER,
                false,
                false
        ));
        return l;
    }

    /** Input component that expands to fill (col 1) */
    public static <T extends Component> T formInput(T c) {
        c.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.FILL,
                GridLayout.Alignment.CENTER,
                true,   // grab extra horizontal space
                false
        ));
        return c;
    }

    /** A component that spans both columns (useful for CheckBox rows, hints, separators) */
    public static <T extends Component> T span2(T c) {
        c.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.FILL,
                GridLayout.Alignment.CENTER,
                true,
                false,
                2,      // horizontal span
                1
        ));
        return c;
    }
}