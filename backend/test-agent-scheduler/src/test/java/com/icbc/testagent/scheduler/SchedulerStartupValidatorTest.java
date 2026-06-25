package com.icbc.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;

class SchedulerStartupValidatorTest {

    @Test
    void disabledSchedulerDoesNotRequireRedis() {
        SchedulerProperties properties = new SchedulerProperties();

        assertThatCode(() -> new SchedulerStartupValidator(properties, new MockEnvironment(), provider(null)).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void enabledSchedulerRequiresRedisFlag() {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setEnabled(true);
        MockEnvironment environment = new MockEnvironment().withProperty("test-agent.redis.enabled", "false");

        assertThatThrownBy(() -> new SchedulerStartupValidator(properties, environment, provider(mock(StringRedisTemplate.class))).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-agent.redis.enabled=true");
    }

    @Test
    void enabledSchedulerRequiresRedisTemplate() {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setEnabled(true);
        MockEnvironment environment = new MockEnvironment().withProperty("test-agent.redis.enabled", "true");

        assertThatThrownBy(() -> new SchedulerStartupValidator(properties, environment, provider(null)).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StringRedisTemplate");
    }

    private ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate redisTemplate) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        return provider;
    }
}
