package com.mrabdul.tools.cachettl;

import com.mrabdul.tools.ToolModule;
import com.mrabdul.tools.ToolScreen;
import org.springframework.stereotype.Component;

@Component
public class CacheTtlInspectorToolModule implements ToolModule {

    private final CacheTtlInspectorScreen screen;

    public CacheTtlInspectorToolModule(CacheTtlInspectorScreen screen) {
        this.screen = screen;
    }

    @Override
    public String id() {
        return "cachettl";
    }

    @Override
    public String name() {
        return "Cache TTL Inspector";
    }

    @Override
    public ToolScreen screen() {
        return screen;
    }
}