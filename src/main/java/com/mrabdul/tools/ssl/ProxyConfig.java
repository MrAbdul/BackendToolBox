package com.mrabdul.tools.ssl;

public class ProxyConfig {
    private final String host;
    private final int port;

    public ProxyConfig(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }

    public boolean isEnabled() {
        return host != null && host.trim().length() > 0 && port > 0;
    }

    public static ProxyConfig none() {
        return new ProxyConfig(null, -1);
    }

    public static ProxyConfig parse(String text) {
        if (text == null) return none();
        String t = text.trim();
        if (t.isEmpty()) return none();
        int idx = t.lastIndexOf(':');
        if (idx <= 0 || idx == t.length() - 1) return none();
        String h = t.substring(0, idx).trim();
        String p = t.substring(idx + 1).trim();
        try {
            int port = Integer.parseInt(p);
            return new ProxyConfig(h, port);
        } catch (Exception e) {
            return none();
        }
    }
}
