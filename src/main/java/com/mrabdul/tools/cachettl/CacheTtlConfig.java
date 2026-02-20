package com.mrabdul.tools.cachettl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CacheTtlConfig {

    public int version = 1;

    public Scan scan = new Scan();
    public List<Detector> detectors = new ArrayList<Detector>();

    public KeyNormalization keyNormalization = new KeyNormalization();
    public TtlResolution ttlResolution = new TtlResolution();

    public Rules rules = new Rules();

    public static class Scan {
        public List<String> excludePathContains = new ArrayList<String>(); // e.g. ["/target/", "/build/"]
    }

    public static class Detector {
        public String id;

        public String methodRegex;
        public String ownerTypeRegex;

        public String operation;
        public String cacheLayer;

        public int keyArgIndex = 0;

        public Integer ttlArgIndex;
        public String ttlUnitDefault;

        // NEW:
        public Integer argCount;  // exact args length
        public Integer minArgs;   // optional
        public Integer maxArgs;   // optional
        public String scopeNameRegex;     // optional
        public String requiredImportRegex; // optional (later)
    }

    public static class KeyNormalization {
        // If key expression is A + ... + ..., namespace is first term.
        public boolean useFirstConcatTermAsNamespace = true;

        // If first term is a NameExpr/FieldAccess and name matches regex, we keep it.
        public String constantNameRegex = ".*(_KEY|_PREFIX)$";

        // If first term is string literal, optionally limit to prefix-like literals:
        public String literalPrefixRegex = ".*"; // e.g. "^[A-Z0-9_\\-]+[:|_].*"
    }

    public static class TtlResolution {
        // Map enum constants / static constants to seconds, e.g. "CACHE_TTL.DAILY" -> 86400
        public Map<String, Long> constantsToSeconds = new HashMap<String, Long>();

        // Recognize java.time.Duration.ofX() patterns
        public boolean parseJavaDurationCalls = true;
    }

    public static class Rules {
        public long veryLongTtlSeconds = 7L * 24L * 3600L; // default 7 days
        public long veryShortTtlSeconds = 30L;             // default 30s

        public boolean flagNoTtlWithoutDelete = true;
        public boolean flagVeryLongTtl = true;
        public boolean flagVeryShortTtl = false;
        public boolean flagDynamicTtl = true;
    }
}