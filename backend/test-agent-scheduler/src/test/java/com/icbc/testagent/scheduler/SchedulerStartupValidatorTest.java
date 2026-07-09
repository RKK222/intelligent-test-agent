package com.icbc.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

class SchedulerStartupValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withBean(SchedulerProperties.class);

    @Test
    void disabledSchedulerDoesNotRequireRedis() {
        SchedulerProperties properties = new SchedulerProperties();

        assertThatCode(() -> new SchedulerStartupValidator(properties, provider(null)).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void blankEnabledPropertyBindsAsDisabled() {
        contextRunner
                .withPropertyValues("test-agent.scheduler.enabled=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SchedulerProperties.class).isEnabled()).isFalse();
                });
    }

    @Test
    void enabledSchedulerDoesNotRequireRedisFlag() {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setEnabled(true);

        assertThatCode(() -> new SchedulerStartupValidator(properties, provider(mock(StringRedisTemplate.class))).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void enabledSchedulerRequiresRedisTemplate() {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setEnabled(true);

        assertThatThrownBy(() -> new SchedulerStartupValidator(properties, provider(null)).validate())
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
