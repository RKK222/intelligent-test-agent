package com.example.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class RedisOptionalHealthIndicatorTest {

    @Test
    void disabledRedisReportsUpWithDisabledDetail() {
        TestAgentRuntimeProperties properties = new TestAgentRuntimeProperties();
        properties.getRedis().setEnabled(false);

        var health = new RedisOptionalHealthIndicator(properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("redis", "disabled");
    }
}
