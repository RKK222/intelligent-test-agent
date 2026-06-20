package com.example.testagent.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.testagent")
public class TestAgentApplication {

    /**
     * 后端唯一启动入口，扫描并装配所有 test-agent 后端模块。
     */
    public static void main(String[] args) {
        SpringApplication.run(TestAgentApplication.class, args);
    }
}
