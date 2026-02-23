package com.mrabdul.tools.lookupdiffer;

import com.mrabdul.tools.ToolModule;
import com.mrabdul.tools.ToolScreen;
import org.springframework.stereotype.Component;

@Component
public class LookupDifferToolModule implements ToolModule {

    private final LookupDifferScreen screen;

    public LookupDifferToolModule(LookupDifferService service) {
        this.screen = new LookupDifferScreen(service);
    }

    @Override
    public String id() {
        return "lookupdiffer";
    }

    @Override
    public String name() {
        return "Lookup Differ";
    }

    @Override
    public ToolScreen screen() {
        return screen;
    }
}