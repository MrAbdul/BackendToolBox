package com.mrabdul.tools.cachettl;

public class CacheNamespaceSummary {
    public String namespaceKey;
    public String cacheLayer;

    public long putCount;
    public long deleteCount;
    public long clearCount;

    public long putWithTtlCount;
    public long putWithoutTtlCount;

    public boolean lifecycleManaged; // true if deletes/clears exist
}