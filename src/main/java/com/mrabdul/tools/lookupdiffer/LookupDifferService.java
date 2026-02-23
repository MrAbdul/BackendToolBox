package com.mrabdul.tools.lookupdiffer;

import org.springframework.stereotype.Service;

@Service
public class LookupDifferService {
    private final LookupDifferEngine engine = new LookupDifferEngine();

    public LookupDifferResult run(LookupDifferRequest req) throws Exception {
        return engine.run(req);
    }
}