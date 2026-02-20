package com.mrabdul.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.WindowDecorationRenderer;
import com.googlecode.lanterna.gui2.WindowPostRenderer;
import com.googlecode.lanterna.gui2.TextBox;
public class OpsTheme implements Theme {

    private final ThemeDefinition def = new OpsThemeDefinition();

    @Override
    public ThemeDefinition getDefaultDefinition() {
        return def;
    }

    @Override
    public ThemeDefinition getDefinition(Class<?> clazz) {
        // TextBox + subclasses (AutoCompleteTextBox)
        if (clazz != null && TextBox.class.isAssignableFrom(clazz)) {
            return new TextBoxThemeDefinition();
        }
        return def;
    }

    @Override
    public WindowPostRenderer getWindowPostRenderer() {
        return null;
    }

    @Override
    public WindowDecorationRenderer getWindowDecorationRenderer() {
        return null;
    }

    private static class TextBoxThemeDefinition implements ThemeDefinition {

        private static final TextColor BG        = new TextColor.RGB(12, 16, 20);
        private static final TextColor FG        = new TextColor.RGB(230, 232, 235);
        private static final TextColor MUTED     = new TextColor.RGB(150, 160, 170);

        // Input backgrounds
        private static final TextColor INPUT_BG  = new TextColor.RGB(20, 26, 32); // visible when inactive
        private static final TextColor ACTIVE_BG = new TextColor.RGB(30, 38, 46); // more visible when focused

        private final SimpleTheme normalTheme   = new SimpleTheme(FG, INPUT_BG);
        private final SimpleTheme activeTheme   = new SimpleTheme(FG, ACTIVE_BG, SGR.BOLD);
        private final SimpleTheme mutedTheme    = new SimpleTheme(MUTED, BG);

        @Override public ThemeStyle getNormal()      { return normalTheme.getDefaultDefinition().getNormal(); }
        @Override public ThemeStyle getPreLight()    { return normalTheme.getDefaultDefinition().getNormal(); }
        @Override public ThemeStyle getSelected()    { return activeTheme.getDefaultDefinition().getNormal(); } // ok
        @Override public ThemeStyle getActive()      { return activeTheme.getDefaultDefinition().getNormal(); }
        @Override public ThemeStyle getInsensitive() { return mutedTheme.getDefaultDefinition().getNormal(); }

        @Override public ThemeStyle getCustom(String name) { return getNormal(); }

        @Override
        public ThemeStyle getCustom(String name, ThemeStyle defaultValue) {
            return defaultValue != null ? defaultValue : getNormal();
        }

        @Override
        public boolean getBooleanProperty(String name, boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public char getCharacter(String name, char defaultValue) {
            return defaultValue;
        }

        @Override
        public <T extends Component> ComponentRenderer<T> getRenderer(Class<T> type) {
            return null;
        }

        @Override
        public boolean isCursorVisible() {
            return true;
        }
    }

    private static class OpsThemeDefinition implements ThemeDefinition {

        // Palette
        private static final TextColor BG        = new TextColor.RGB(12, 16, 20);
        private static final TextColor FG        = new TextColor.RGB(230, 232, 235);
        private static final TextColor MUTED     = new TextColor.RGB(150, 160, 170);

        private static final TextColor ACCENT_BG = new TextColor.RGB(206, 171, 86);
        private static final TextColor ACCENT_FG = new TextColor.RGB(12, 16, 20);

        private static final TextColor ACTIVE_BG = new TextColor.RGB(25, 32, 40);

        // We create SimpleTheme objects and ask them for ThemeStyle
        private final SimpleTheme normalTheme   = new SimpleTheme(FG, BG);
        private final SimpleTheme preLightTheme = new SimpleTheme(FG, ACTIVE_BG);
        private final SimpleTheme activeTheme   = new SimpleTheme(FG, ACTIVE_BG, SGR.BOLD);
        private final SimpleTheme selectedTheme = new SimpleTheme(ACCENT_FG, ACCENT_BG, SGR.BOLD);
        private final SimpleTheme mutedTheme    = new SimpleTheme(MUTED, BG);

        @Override public ThemeStyle getNormal()      { return normalTheme.getDefaultDefinition().getNormal(); }
        @Override public ThemeStyle getPreLight()    { return preLightTheme.getDefaultDefinition().getNormal(); }
        @Override public ThemeStyle getSelected()    { return selectedTheme.getDefaultDefinition().getNormal(); }
        @Override public ThemeStyle getActive()      { return activeTheme.getDefaultDefinition().getNormal(); }
        @Override public ThemeStyle getInsensitive() { return mutedTheme.getDefaultDefinition().getNormal(); }

        @Override
        public ThemeStyle getCustom(String name) {
            return getNormal();
        }

        // ---- Older Lanterna versions have extra methods; implement safely ----

        @Override
        public ThemeStyle getCustom(String name, ThemeStyle defaultValue) {
            return defaultValue != null ? defaultValue : getNormal();
        }

        @Override
        public boolean getBooleanProperty(String name, boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public char getCharacter(String name, char defaultValue) {
            return defaultValue;
        }

        @Override
        public <T extends Component> ComponentRenderer<T> getRenderer(Class<T> type) {
            return null;
        }

        @Override
        public boolean isCursorVisible() {
            return true;
        }
    }
}