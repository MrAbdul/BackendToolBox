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
        if (keyStroke != null) {

            // Ctrl+Space (most reliable)
            if (keyStroke.getKeyType() == KeyType.Character
                    && keyStroke.isCtrlDown()
                    && keyStroke.getCharacter() != null
                    && keyStroke.getCharacter().charValue() == ' ') {
                FilePathAutoCompleter.autocomplete(this);
                return Result.HANDLED;
            }

            // F2 as an alternative (also reliable in most terminals)
            if (keyStroke.getKeyType() == KeyType.F2) {
                FilePathAutoCompleter.autocomplete(this);
                return Result.HANDLED;
            }

            // Best-effort: if TAB comes as a characters
            if (keyStroke.getKeyType() == KeyType.Character
                    && keyStroke.getCharacter() != null
                    && keyStroke.getCharacter().charValue() == '\t') {
                FilePathAutoCompleter.autocomplete(this);
                return Result.HANDLED;
            }
        }

        return super.handleKeyStroke(keyStroke);
    }
}