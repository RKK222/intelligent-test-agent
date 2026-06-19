package com.example.testagent.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.testagent")
public class TestAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestAgentApplication.class, args);
    }
}
