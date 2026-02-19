package com.mrabdul.tools.jdbcdetector;

import com.mrabdul.tools.ToolModule;
import com.mrabdul.tools.ToolScreen;
import org.springframework.stereotype.Component;

@Component
public class JdbcDetectorToolModule implements ToolModule {

    private final JdbcDetectorScreen screen;

    public JdbcDetectorToolModule(JdbcDetectorService service) {
        this.screen = new JdbcDetectorScreen(service);
    }

    @Override
    public String id() {
        return "jdbcdetector";
    }

    @Override
    public String name() {
        return "JDBC Detector";
    }

    @Override
    public ToolScreen screen() {
        return screen;
    }
}
