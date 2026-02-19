package com.mrabdul.tools.hello;

import com.mrabdul.tools.ToolModule;
import com.mrabdul.tools.ToolScreen;
import org.springframework.stereotype.Component;

@Component
public class HelloToolModule implements ToolModule {
    private final HelloScreen screen;

    public HelloToolModule(HelloScreen screen) {
        this.screen = screen;
    }

    public String id() {
        return "hello";
    }

    public String name() {
        return "Hello Tool";
    }

    public ToolScreen screen() {
        return screen;
    }

}
