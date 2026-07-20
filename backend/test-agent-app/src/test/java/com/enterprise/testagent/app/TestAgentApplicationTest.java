package com.enterprise.testagent.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

class TestAgentApplicationTest {

    @Test
    void mainApplicationIsAlwaysReactiveAndUsesShanghaiTimeZone() {
        TimeZone previous = TimeZone.getDefault();
        try {
            SpringApplication application = TestAgentApplication.createApplication();

            assertThat(application.getWebApplicationType()).isEqualTo(WebApplicationType.REACTIVE);
            assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Shanghai");
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
