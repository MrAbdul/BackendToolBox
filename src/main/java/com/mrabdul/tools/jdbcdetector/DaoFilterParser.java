package com.mrabdul.tools.jdbcdetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DaoFilterParser {
    private DaoFilterParser() {}

    public static List<String> parseCommaSeparated(String raw) {
        if (raw == null) return Collections.emptyList();
        String s = raw.trim();
        if (s.isEmpty()) return Collections.emptyList();

        String[] parts = s.split(",");
        List<String> out = new ArrayList<String>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public static String simpleName(String type) {
        if (type == null) return "";
        int idx = type.lastIndexOf('.');
        return idx >= 0 ? type.substring(idx + 1) : type;
    }
}
