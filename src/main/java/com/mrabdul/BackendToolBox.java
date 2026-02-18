package com.mrabdul;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class BackendToolBox {
    public static void main(String[] args) {
        new SpringApplicationBuilder(BackendToolBox.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
