package com.mrabdul.tools.jdbcdetector;

import org.springframework.stereotype.Service;

@Service
public class JdbcDetectorService {
    private final JdbcDetectorEngine engine = new JdbcDetectorEngine();

    public JdbcDetectorResult run(JdbcDetectorRequest req) throws Exception {
        return engine.run(req);
    }
}
