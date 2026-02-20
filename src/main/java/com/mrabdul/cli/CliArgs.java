package com.mrabdul.cli;

import java.util.LinkedHashMap;
import java.util.Map;
//an args helper
public final class CliArgs {
    private CliArgs() {}

    public static Map<String, String> parse(String[] args) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (args == null) return m;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a == null) continue;

            if (a.startsWith("--")) {
                String key = a;
                String val = "true";

                int eq = a.indexOf('=');
                if (eq > 2) {
                    key = a.substring(2, eq);
                    val = a.substring(eq + 1);
                } else {
                    key = a.substring(2);
                    if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].startsWith("--")) {
                        val = args[++i];
                    }
                }
                m.put(key, val);
            }
        }
        return m;
    }

    public static String get(Map<String, String> m, String key, String def) {
        String v = m.get(key);
        return v == null ? def : v;
    }

    public static boolean getBool(Map<String, String> m, String key, boolean def) {
        String v = m.get(key);
        if (v == null) return def;
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }
}