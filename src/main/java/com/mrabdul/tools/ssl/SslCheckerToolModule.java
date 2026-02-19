package com.mrabdul.tools.ssl;

import com.mrabdul.tools.ToolModule;
import com.mrabdul.tools.ToolScreen;
import org.springframework.stereotype.Component;

@Component
public class SslCheckerToolModule implements ToolModule {

    private final SslCheckerScreen screen;

    public SslCheckerToolModule(SslCheckerScreen screen) {
        this.screen = screen;
    }

    public String id() { return "ssl-checker"; }

    public String name() { return "SSL Checker"; }

    public ToolScreen screen() { return screen; }
}
