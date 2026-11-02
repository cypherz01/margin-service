package com.example.margin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MarginServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarginServiceApplication.class, args);
    }
}
