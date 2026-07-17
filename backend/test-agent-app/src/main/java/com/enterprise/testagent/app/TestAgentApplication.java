package com.enterprise.testagent.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.enterprise.testagent")
@EnableScheduling
@MapperScan("com.enterprise.testagent.persistence.mybatis")
public class TestAgentApplication {

    /**
     * 后端唯一启动入口，扫描并装配所有 test-agent 后端模块。
     */
    public static void main(String[] args) {
        SpringApplication.run(TestAgentApplication.class, args);
    }
}
