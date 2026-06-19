package com.example.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TestAgentRuntimePropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withBean(TestAgentRuntimeProperties.class);

    @Test
    void bindsRuntimePropertiesFromEnvironmentStyleValues() {
        contextRunner
                .withPropertyValues(
                        "test-agent.security.api-token=secret",
                        "test-agent.security.cors-allowed-origins=http://localhost:3000,http://127.0.0.1:3000",
                        "test-agent.rate-limit.enabled=true",
                        "test-agent.rate-limit.capacity=5",
                        "test-agent.rate-limit.window=2s",
                        "test-agent.redis.enabled=true",
                        "test-agent.redis.host=localhost",
                        "test-agent.redis.port=16379",
                        "test-agent.opencode.nodes[0].id=node_local",
                        "test-agent.opencode.nodes[0].base-url=http://127.0.0.1:4096",
                        "test-agent.opencode.nodes[0].max-runs=3",
                        "test-agent.opencode.nodes[0].weight=20",
                        "test-agent.opencode.nodes[0].capabilities=chat,diff")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

                    assertThat(properties.getSecurity().getApiToken()).isEqualTo("secret");
                    assertThat(properties.getSecurity().getCorsAllowedOrigins())
                            .containsExactly("http://localhost:3000", "http://127.0.0.1:3000");
                    assertThat(properties.getRateLimit().isEnabled()).isTrue();
                    assertThat(properties.getRateLimit().getCapacity()).isEqualTo(5);
                    assertThat(properties.getRateLimit().getWindow()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.getRedis().isEnabled()).isTrue();
                    assertThat(properties.getRedis().getPort()).isEqualTo(16379);
                    assertThat(properties.getOpencode().getNodes()).hasSize(1);
                    assertThat(properties.getOpencode().getNodes().get(0).getCapabilities())
                            .containsExactly("chat", "diff");
                });
    }
}
