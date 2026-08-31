package org.lawnpilot.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("org.lawnpilot")
public class LawnPilotApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LawnPilotApiApplication.class, args);
    }
}
