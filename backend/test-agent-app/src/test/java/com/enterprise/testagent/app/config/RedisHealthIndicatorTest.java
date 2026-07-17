package com.enterprise.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.health.contributor.Status;

class RedisHealthIndicatorTest {

    @Test
    void redisHealthReportsRequiredConnectionStatus() {
        DataRedisProperties properties = new DataRedisProperties();
        properties.setPort(1);

        var health = new RedisHealthIndicator(properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("redis", "required");
    }
}
