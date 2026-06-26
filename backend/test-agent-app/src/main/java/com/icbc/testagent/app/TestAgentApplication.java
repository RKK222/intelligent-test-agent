package com.icbc.testagent.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@SpringBootApplication(scanBasePackages = "com.icbc.testagent")
@PropertySources({
    @PropertySource(value = "classpath:ssh-key.key", ignoreResourceNotFound = true)
})
public class TestAgentApplication {

    /**
     * 后端唯一启动入口，扫描并装配所有 test-agent 后端模块。
     */
    public static void main(String[] args) {
        SpringApplication.run(TestAgentApplication.class, args);
    }
}
