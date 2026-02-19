package com.mrabdul.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class AutoCompleteTextBox extends TextBox {

    public AutoCompleteTextBox() {
        super();
    }

    public AutoCompleteTextBox(int preferredColumnCount, int preferedRowsCount) {
        super(new TerminalSize(preferredColumnCount,preferedRowsCount));
    }

    @Override
    public synchronized Result handleKeyStroke(KeyStroke keyStroke) {
        if (keyStroke != null && keyStroke.getKeyType() == KeyType.Tab) {
            FilePathAutoCompleter.autocomplete(this);
            return Result.HANDLED;
        }
        return super.handleKeyStroke(keyStroke);
    }
}
