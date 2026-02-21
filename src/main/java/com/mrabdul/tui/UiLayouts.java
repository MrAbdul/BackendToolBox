package com.mrabdul.tui;

import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;

public final class UiLayouts {
    private UiLayouts() {}

    /** Left-aligned label (col 0) */
    public static Label formLabel(String text) {
        Label l = new Label(text);
        l.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.BEGINNING,
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
                true,
                false
        ));
        return c;
    }

    /** A component that spans both columns */
    public static <T extends Component> T span2(T c) {
        c.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.BEGINNING,
                GridLayout.Alignment.CENTER,
                true,
                false,
                2,
                1
        ));
        return c;
    }
}