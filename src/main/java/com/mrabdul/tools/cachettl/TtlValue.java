package com.mrabdul.tools.cachettl;

public class TtlValue {
    public final Long seconds;

    private TtlValue(Long seconds) {
        this.seconds = seconds;
    }

    public static TtlValue seconds(Long s) {
        return new TtlValue(s);
    }

    public static TtlValue unknown() {
        return new TtlValue(null);
    }
}