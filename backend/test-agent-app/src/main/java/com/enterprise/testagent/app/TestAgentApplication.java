package com.enterprise.testagent.app;

import java.time.ZoneId;
import java.util.TimeZone;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
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
        createApplication().run(args);
    }

    /**
     * XXL Admin 引入 Servlet 依赖后仍强制平台主上下文使用 WebFlux，并统一 Java 进程时区。
     */
    static SpringApplication createApplication() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai")));
        SpringApplication application = new SpringApplication(TestAgentApplication.class);
        application.setWebApplicationType(WebApplicationType.REACTIVE);
        return application;
    }
}
