package com.mrabdul.tools.cachettl;

public class CacheOperation {
    public String opType;     // PUT/DELETE/CLEAR/GET
    public String cacheLayer; // REDIS/LOCAL/ANY/UNKNOWN

    public String file;
    public String className;
    public String methodName;
    public int line;

    public String methodCalled;   // best-effort: owner + method (if resolvable)
    public String keyExpr;        // expression as string
    public String namespaceKey;   // fingerprint / namespace
    public String ttlExpr;        // expression as string (if any)
    public Long ttlSeconds;       // resolved seconds (if resolvable)
}