package com.mrabdul.tools.cachettl;

import org.springframework.stereotype.Service;

@Service
public class CacheTtlInspectorService {

    private final CacheTtlInspectorEngine engine = new CacheTtlInspectorEngine();

    public CacheTtlInspectorResult run(CacheTtlInspectorRequest req) throws Exception {
        return engine.run(req);
    }

}