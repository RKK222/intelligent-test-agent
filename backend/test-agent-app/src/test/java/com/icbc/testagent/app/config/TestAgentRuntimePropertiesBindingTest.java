package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.scheduler.SchedulerProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TestAgentRuntimePropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withBean(TestAgentRuntimeProperties.class);

    private final ApplicationContextRunner profileContextRunner = contextRunner
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void defaultCorsAllowsLocalhostAndLoopbackFrontendOrigins() {
        contextRunner.run(context -> {
            TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

            assertThat(properties.getSecurity().getCorsAllowedOrigins())
                    .contains("http://localhost:3000", "http://127.0.0.1:3000", "http://127.0.0.1:4187");
        });
    }

    @Test
    void defaultDruidConfigValidatesBorrowedConnections() {
        profileContextRunner.run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.datasource.druid.validation-query"))
                    .isEqualTo("SELECT 1");
            assertThat(context.getEnvironment().getProperty("spring.datasource.druid.test-on-borrow", Boolean.class))
                    .isTrue();
            assertThat(context.getEnvironment().getProperty("spring.datasource.druid.test-while-idle", Boolean.class))
                    .isTrue();
        });
    }

    @Test
    void defaultSchedulerScanningIsDisabled() {
        profileContextRunner
                .withBean(SchedulerProperties.class)
                .run(context -> assertThat(context.getBean(SchedulerProperties.class).isEnabled()).isFalse());
    }

    @Test
    void defaultTerminalSafetyLimitsAreBounded() {
        contextRunner.run(context -> {
            TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

            assertThat(properties.getTerminal().getMaxInputBytes()).isEqualTo(16 * 1024);
            assertThat(properties.getTerminal().getInputMessagesPerWindow()).isEqualTo(64);
            assertThat(properties.getTerminal().getResizeMessagesPerWindow()).isEqualTo(10);
            assertThat(properties.getTerminal().getRateLimitWindow()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.getTerminal().getMaxOutputFrameBytes()).isEqualTo(16 * 1024);
            assertThat(properties.getTerminal().getMaxOutputConnectionBytes()).isEqualTo(1024 * 1024);
            assertThat(properties.getTerminal().getIdleTimeout()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.getTerminal().getHardTimeout()).isEqualTo(Duration.ofHours(2));
            assertThat(properties.getTerminal().getTicketCapacity()).isEqualTo(10);
            assertThat(properties.getTerminal().getTicketWindow()).isEqualTo(Duration.ofMinutes(1));
        });
    }

    @Test
    void defaultManagerControlSettingsAreLocalAndTokenless() {
        contextRunner.run(context -> {
            TestAgentRuntimeProperties.ManagerControl managerControl = context
                    .getBean(TestAgentRuntimeProperties.class)
                    .getOpencode()
                    .getManagerControl();

            assertThat(managerControl.getToken()).isEmpty();
            assertThat(managerControl.getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(5));
            assertThat(managerControl.getBackendStaleAfter()).isEqualTo(Duration.ofSeconds(10));
            assertThat(managerControl.getCommandTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void ymlConfigurationDoesNotExposeDeprecatedOpencodeNodeOrListenUrlProperties() {
        profileContextRunner.run(context -> {
            assertThat(context.getEnvironment().getProperty("test-agent.opencode.manager-control.listen-url"))
                    .isNull();
            assertThat(context.getEnvironment().getProperty("test-agent.opencode." + "nodes[0].id"))
                    .isNull();
            assertThat(context.getEnvironment().getProperty("test-agent.opencode." + "nodes[0].base-url"))
                    .isNull();
        });
    }

    @Test
    void ymlConfigurationDoesNotExposeDeprecatedWorkspaceDirectoryOrRedisProperties() {
        profileContextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "TEST_AGENT_DB_URL=jdbc:postgresql://prod-postgres.example.internal:5432/test_agent",
                        "TEST_AGENT_DB_USERNAME=test_agent",
                        "TEST_AGENT_DB_PASSWORD=secret",
                        "TEST_AGENT_API_TOKEN=api-token",
                        "TEST_AGENT_CORS_ALLOWED_ORIGINS=https://agent.example.com",
                        "TEST_AGENT_REDIS_HOST=prod-redis.example.internal",
                        "TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-secret")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("test-agent.managed-workspace." + "root"))
                            .isNull();
                    assertThat(context.getEnvironment().getProperty("test-agent." + "public-directory." + "path"))
                            .isNull();
                    assertThat(context.getEnvironment().getProperty("test-agent." + "redis.host"))
                            .isNull();
                    assertThat(context.getEnvironment().getProperty("test-agent." + "redis.port"))
                            .isNull();
                    assertThat(context.getEnvironment().getProperty("test-agent.workspace-" + "picker." + "allowed-" + "roots"))
                            .isNull();
                    assertThat(context.getEnvironment().getProperty("spring.data.redis.host"))
                            .isEqualTo("prod-redis.example.internal");
                });
    }

    @Test
    void bindsRuntimePropertiesFromEnvironmentStyleValues() {
        contextRunner
                .withPropertyValues(
                        "test-agent.security.api-token=secret",
                        "test-agent.security.cors-allowed-origins=http://localhost:3000,http://127.0.0.1:3000",
                        "test-agent.rate-limit.enabled=true",
                        "test-agent.rate-limit.capacity=5",
                        "test-agent.rate-limit.window=2s",
                        "test-agent.terminal.max-input-bytes=1024",
                        "test-agent.terminal.input-messages-per-window=8",
                        "test-agent.terminal.resize-messages-per-window=3",
                        "test-agent.terminal.rate-limit-window=500ms",
                        "test-agent.terminal.max-output-frame-bytes=2048",
                        "test-agent.terminal.max-output-connection-bytes=4096",
                        "test-agent.terminal.idle-timeout=30s",
                        "test-agent.terminal.hard-timeout=5m",
                        "test-agent.terminal.ticket-capacity=2",
                        "test-agent.terminal.ticket-window=10s",
                        "test-agent.opencode.manager-control.token=manager-secret",
                        "test-agent.opencode.manager-control.heartbeat-interval=4s",
                        "test-agent.opencode.manager-control.backend-stale-after=9s",
                        "test-agent.opencode.manager-control.command-timeout=7s")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

                    assertThat(properties.getSecurity().getApiToken()).isEqualTo("secret");
                    assertThat(properties.getSecurity().getCorsAllowedOrigins())
                            .containsExactly("http://localhost:3000", "http://127.0.0.1:3000");
                    assertThat(properties.getRateLimit().isEnabled()).isTrue();
                    assertThat(properties.getRateLimit().getCapacity()).isEqualTo(5);
                    assertThat(properties.getRateLimit().getWindow()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.getTerminal().getMaxInputBytes()).isEqualTo(1024);
                    assertThat(properties.getTerminal().getInputMessagesPerWindow()).isEqualTo(8);
                    assertThat(properties.getTerminal().getResizeMessagesPerWindow()).isEqualTo(3);
                    assertThat(properties.getTerminal().getRateLimitWindow()).isEqualTo(Duration.ofMillis(500));
                    assertThat(properties.getTerminal().getMaxOutputFrameBytes()).isEqualTo(2048);
                    assertThat(properties.getTerminal().getMaxOutputConnectionBytes()).isEqualTo(4096);
                    assertThat(properties.getTerminal().getIdleTimeout()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.getTerminal().getHardTimeout()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(properties.getTerminal().getTicketCapacity()).isEqualTo(2);
                    assertThat(properties.getTerminal().getTicketWindow()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(properties.getOpencode().getManagerControl().getToken()).isEqualTo("manager-secret");
                    assertThat(properties.getOpencode().getManagerControl().getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(4));
                    assertThat(properties.getOpencode().getManagerControl().getBackendStaleAfter()).isEqualTo(Duration.ofSeconds(9));
                    assertThat(properties.getOpencode().getManagerControl().getCommandTimeout()).isEqualTo(Duration.ofSeconds(7));
                });
    }

    @Test
    void testProfileBindsExternalDatabaseAndOpencodeNode() {
        profileContextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "TEST_AGENT_TEST_DB_HOST=test-postgres.example.internal",
                        "TEST_AGENT_TEST_DB_PORT=25432",
                        "TEST_AGENT_TEST_DB_NAME=test_agent_ci",
                        "TEST_AGENT_TEST_DB_USERNAME=test_agent",
                        "TEST_AGENT_TEST_DB_PASSWORD=secret",
                        "TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-secret")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

                    assertThat(context.getEnvironment().getProperty("spring.datasource.druid.url"))
                            .isEqualTo("jdbc:postgresql://test-postgres.example.internal:25432/test_agent_ci");
                    assertThat(properties.getOpencode().getManagerControl().getToken()).isEqualTo("manager-secret");
                });
    }

    @Test
    void prodProfileBindsExternalServicesWithoutBundledDatabaseOrRedis() {
        profileContextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "TEST_AGENT_DB_URL=jdbc:postgresql://prod-postgres.example.internal:5432/test_agent",
                        "TEST_AGENT_DB_USERNAME=test_agent",
                        "TEST_AGENT_DB_PASSWORD=secret",
                        "TEST_AGENT_API_TOKEN=api-token",
                        "TEST_AGENT_CORS_ALLOWED_ORIGINS=https://agent.example.com",
                        "TEST_AGENT_REDIS_HOST=prod-redis.example.internal",
                        "TEST_AGENT_REDIS_PORT=6379",
                        "TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-secret")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

                    assertThat(context.getEnvironment().getProperty("spring.datasource.druid.url"))
                            .isEqualTo("jdbc:postgresql://prod-postgres.example.internal:5432/test_agent");
                    assertThat(properties.getSecurity().getApiToken()).isEqualTo("api-token");
                    assertThat(properties.getSecurity().getCorsAllowedOrigins())
                            .containsExactly("https://agent.example.com");
                    assertThat(context.getEnvironment().getProperty("spring.data.redis.host"))
                            .isEqualTo("prod-redis.example.internal");
                    assertThat(context.getEnvironment().getProperty("spring.data.redis.port", Integer.class))
                            .isEqualTo(6379);
                    assertThat(properties.getOpencode().getManagerControl().getToken()).isEqualTo("manager-secret");
                });
    }
}
