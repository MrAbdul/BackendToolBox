package com.mrabdul.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class EscapableActionListBox extends ActionListBox {

    private Runnable onEscape;

    public EscapableActionListBox() {
        super();
    }

    public EscapableActionListBox(int preferredWidth, int preferredHeight) {
        super(new TerminalSize(preferredWidth, preferredHeight));
    }

    public void setOnEscape(Runnable r) {
        this.onEscape = r;
    }

    @Override
    public synchronized Result handleKeyStroke(KeyStroke keyStroke) {
        if (keyStroke != null && keyStroke.getKeyType() == KeyType.Escape) {
            if (onEscape != null) onEscape.run();
            return Result.HANDLED;
        }
        return super.handleKeyStroke(keyStroke);
    }
}
