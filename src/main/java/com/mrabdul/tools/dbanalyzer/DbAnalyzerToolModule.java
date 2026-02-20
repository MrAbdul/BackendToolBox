package com.mrabdul.tools.dbanalyzer;

import com.mrabdul.tools.ToolModule;
import com.mrabdul.tools.ToolScreen;
import org.springframework.stereotype.Component;

@Component
public class DbAnalyzerToolModule implements ToolModule {

    private final DbAnalyzerScreen screen;

    public DbAnalyzerToolModule(DbAnalyzerScreen screen) {
        this.screen = screen;
    }

    @Override
    public String id() {
        return "dbanalyzer";
    }

    @Override
    public String name() {
        return "DB Analyzer (SQL Diff)";
    }

    @Override
    public ToolScreen screen() {
        return screen;
    }
}
