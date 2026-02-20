// com.mrabdul.tui.UiTheme.java
package com.mrabdul.tui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.Theme;

public final class UiTheme {
    private UiTheme() {}

    // Pick a modern palette. Replace with Boubyan tones you like.
    public static final TextColor BG = new TextColor.RGB(10, 14, 18);         // near-black
    public static final TextColor FG = new TextColor.RGB(230, 232, 235);      // off-white
    public static final TextColor ACCENT = new TextColor.RGB(206, 171, 86);   // gold
    public static final TextColor MUTED = new TextColor.RGB(140, 150, 160);   // muted gray
    public static final TextColor ERROR = new TextColor.RGB(220, 70, 70);
    public static final TextColor WARN  = new TextColor.RGB(230, 170, 70);

    public static Theme theme() {
        // SimpleTheme(background, foreground)
        return new SimpleTheme(BG, FG);
    }
}