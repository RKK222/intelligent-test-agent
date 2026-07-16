package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConversationContextPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withBean(ConversationContextProperties.class);

    @Test
    void defaultsKeepNewStorageDisabledAndLegacyClientsEnabled() {
        contextRunner.run(context -> {
            ConversationContextProperties properties = context.getBean(ConversationContextProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getRolloutPercentage()).isZero();
            assertThat(properties.isLegacyRunWithoutContextEnabled()).isTrue();
        });
    }

    @Test
    void bindsRuntimeOwnedConversationContextPolicy() {
        contextRunner
                .withPropertyValues(
                        "test-agent.redis-summary.enabled=true",
                        "test-agent.redis-summary.rollout-percentage=25",
                        "test-agent.redis-summary.legacy-run-without-context-enabled=false")
                .run(context -> {
                    ConversationContextProperties properties = context.getBean(ConversationContextProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getRolloutPercentage()).isEqualTo(25);
                    assertThat(properties.isLegacyRunWithoutContextEnabled()).isFalse();
                });
    }
}
