package com.mrabdul.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;

public final class UiForms {

    private UiForms() {}

    public static Panel twoColumnForm() {
        GridLayout grid = new GridLayout(2);
        grid.setHorizontalSpacing(1);
        grid.setVerticalSpacing(1);
        return new Panel(grid);
    }

    public static void row(Panel form, String labelText, Component field) {
        Label label = new Label(labelText);
        label.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.BEGINNING,
                GridLayout.Alignment.CENTER,
                false,
                false
        ));
        form.addComponent(label);

        field.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.FILL,
                GridLayout.Alignment.CENTER,
                true,
                false
        ));
        form.addComponent(field);
    }

    public static void span2(Panel form, Component component) {
        component.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.BEGINNING,
                GridLayout.Alignment.CENTER,
                true,
                false,
                2,
                1
        ));
        form.addComponent(component);
    }

    /** Standard action row: [primary] [space] [secondary] */
    public static Panel actionsRow(Button primary, Button secondary) {
        Panel actions = new Panel(new LinearLayout(Direction.HORIZONTAL));
        actions.addComponent(primary);
        actions.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actions.addComponent(secondary);
        return actions;
    }
}