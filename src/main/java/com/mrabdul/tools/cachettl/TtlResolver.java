package com.mrabdul.tools.cachettl;

import com.github.javaparser.ast.expr.*;

public final class TtlResolver {

    private TtlResolver() {}

    public static TtlValue resolve(Expression ttlExpr, String unitDefault, CacheTtlConfig.TtlResolution cfg) {
        if (ttlExpr == null) return TtlValue.unknown();

        // constants mapping
        if (cfg != null && cfg.constantsToSeconds != null) {
            String key = ttlExpr.toString();
            if (cfg.constantsToSeconds.containsKey(key)) {
                return TtlValue.seconds(cfg.constantsToSeconds.get(key));
            }
        }

        // numeric literal
        Long numeric = parseLongLiteral(ttlExpr);
        if (numeric != null) {
            long secs = applyUnit(numeric.longValue(), unitDefault);
            return TtlValue.seconds(secs);
        }

        // Duration.ofX(...)
        if (cfg != null && cfg.parseJavaDurationCalls) {
            Long dur = parseDurationOf(ttlExpr);
            if (dur != null) return TtlValue.seconds(dur);
        }

        return TtlValue.unknown();
    }

    private static Long parseLongLiteral(Expression e) {
        try {
            if (e instanceof IntegerLiteralExpr) {
                return Long.valueOf(((IntegerLiteralExpr) e).asNumber().longValue());
            }
            if (e instanceof LongLiteralExpr) {
                // JavaParser stores as string with "L" sometimes
                String raw = ((LongLiteralExpr) e).getValue();
                return Long.valueOf(raw);
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static long applyUnit(long v, String unitDefault) {
        if (unitDefault == null) return v;
        String u = unitDefault.trim().toUpperCase();
        if ("SECONDS".equals(u)) return v;
        if ("MINUTES".equals(u)) return v * 60L;
        if ("HOURS".equals(u)) return v * 3600L;
        if ("DAYS".equals(u)) return v * 86400L;
        return v;
    }

    private static Long parseDurationOf(Expression e) {
        // Duration.ofSeconds(10) / ofMinutes(5) / ofHours(1) / ofDays(1)
        if (!(e instanceof MethodCallExpr)) return null;
        MethodCallExpr mc = (MethodCallExpr) e;

        String name = mc.getNameAsString();
        if (!name.startsWith("of")) return null;

        // scope should be Duration (best-effort)
        if (!mc.getScope().isPresent()) return null;
        Expression scope = mc.getScope().get();
        if (!"Duration".equals(scope.toString()) && !"java.time.Duration".equals(scope.toString())) {
            return null;
        }

        if (mc.getArguments() == null || mc.getArguments().isEmpty()) return null;
        Long n = parseLongLiteral(mc.getArgument(0));
        if (n == null) return null;

        if ("ofSeconds".equals(name)) return n;
        if ("ofMinutes".equals(name)) return n * 60L;
        if ("ofHours".equals(name)) return n * 3600L;
        if ("ofDays".equals(name)) return n * 86400L;

        return null;
    }
}