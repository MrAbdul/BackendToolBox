package com.mrabdul.tools.jdbcdetector;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;

class JdbcDetectorEngineTest {
    @Test
    void smokeRunDoesNotCrash() throws Exception {
        JdbcDetectorEngine engine = new JdbcDetectorEngine();
        JdbcDetectorRequest req = new JdbcDetectorRequest(
                "src/main/java", // limit parsing to project sources
                Collections.emptyList(),
                true,
                true,
                null
        );
        JdbcDetectorResult result = engine.run(req);
        assertNotNull(result);
        assertNotNull(result.getFindings());
    }
}