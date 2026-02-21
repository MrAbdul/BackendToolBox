package com.mrabdul.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.TextBox;

public final class UiSizes {
    private UiSizes() {}

    // --- Global sizing rules ---
    // Keep consistent across all screens

    /** Standard width for input fields (paths, urls, etc.) */
    public static final int INPUT_WIDE  = 90;

    /** Standard width for “medium” inputs (password, proxy host:port) */
    public static final int INPUT_MED   = 40;

    /** Standard report/output box size */
    public static final int REPORT_W    = 120;
    public static final int REPORT_H    = 24;

    /** Sidebar width */
    public static final int MENU_W      = 28;

    /** Rough menu height baseline */
    public static final int MENU_H      = 25;

    public static TerminalSize inputWide()  { return new TerminalSize(INPUT_WIDE, 1); }
    public static TerminalSize inputMed()   { return new TerminalSize(INPUT_MED, 1); }
    public static TerminalSize reportSize() { return new TerminalSize(REPORT_W, REPORT_H); }

    public static TextBox reportBox() {
        TextBox t = new TextBox(reportSize(), TextBox.Style.MULTI_LINE);
        t.setReadOnly(true);
        return t;
    }
}