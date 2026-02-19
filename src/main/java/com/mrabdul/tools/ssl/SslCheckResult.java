package com.mrabdul.tools.ssl;

public class SslCheckResult {
    private final boolean ok;
    private final String report;

    public SslCheckResult(boolean ok, String report) {
        this.ok = ok;
        this.report = report;
    }

    public boolean isOk() { return ok; }
    public String getReport() { return report; }

    public static SslCheckResult ok(String report) { return new SslCheckResult(true, report); }
    public static SslCheckResult fail(String report) { return new SslCheckResult(false, report); }
}
