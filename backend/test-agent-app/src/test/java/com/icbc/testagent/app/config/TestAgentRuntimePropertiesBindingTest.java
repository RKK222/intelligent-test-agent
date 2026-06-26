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
    void guoProfileCorsAllowsEnvironmentOverrideForLanFrontendOrigin() {
        profileContextRunner
                .withPropertyValues(
                        "spring.profiles.active=guo",
                        "TEST_AGENT_CORS_ALLOWED_ORIGINS=http://192.168.100.115:3000,http://127.0.0.1:3000")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

                    assertThat(properties.getSecurity().getCorsAllowedOrigins())
                            .containsExactly("http://192.168.100.115:3000", "http://127.0.0.1:3000");
                });
    }

    @Test
    void guoProfileCorsAllowsDefaultLanFrontendOriginForIdeaStartup() {
        profileContextRunner
                .withPropertyValues("spring.profiles.active=guo")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

                    assertThat(properties.getSecurity().getCorsAllowedOrigins())
                            .contains("http://192.168.100.115:3000", "http://127.0.0.1:3000");
                });
    }

    @Test
    void guoProfileBindsIdeaRunnableServiceConfigurationFromYaml() {
        profileContextRunner
                .withPropertyValues("spring.profiles.active=guo")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

                    assertThat(context.getEnvironment().getProperty("spring.datasource.druid.url"))
                            .isEqualTo("jdbc:postgresql://127.0.0.1:15432/testagent?sslmode=disable");
                    assertThat(context.getEnvironment().getProperty("test-agent.model-catalog.external.api-key"))
                            .isNotBlank();
                    assertThat(properties.getRedis().getHost()).isEqualTo("192.168.100.115");
                    assertThat(properties.getRedis().getPort()).isEqualTo(16379);
                    assertThat(properties.getWorkspacePicker().getAllowedRoots())
                            .contains("/Users/kaka/Desktop", "D:/workspace");
                    assertThat(properties.getOpencode().getManagerControl().getToken()).isEqualTo("local-manager-token");
                    assertThat(properties.getOpencode().getManagerControl().getGatewayMode()).isEqualTo("local");
                    assertThat(properties.getOpencode().getLocalDirectBaseUrl()).isEqualTo("http://192.168.100.115:4096");
                    assertThat(properties.getOpencode().getNodes()).hasSize(1);
                    assertThat(properties.getOpencode().getNodes().getFirst().getBaseUrl())
                            .isEqualTo("http://192.168.100.115:4096");
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
            assertThat(managerControl.getListenUrl()).isEqualTo("http://127.0.0.1:8080");
            assertThat(managerControl.getLinuxServerId()).isEqualTo("127.0.0.1");
            assertThat(managerControl.getCommandTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void workspacePickerAllowedRootsCanBeBoundFromEnvironmentStyleValues() {
        contextRunner
                .withPropertyValues("test-agent.workspace-picker.allowed-roots=/Users/huang/workspace,/tmp/projects")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

                    assertThat(properties.getWorkspacePicker().getAllowedRoots())
                            .containsExactly("/Users/huang/workspace", "/tmp/projects");
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
                        "test-agent.redis.enabled=true",
                        "test-agent.redis.host=localhost",
                        "test-agent.redis.port=16379",
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
                        "test-agent.opencode.nodes[0].id=node_local",
                        "test-agent.opencode.nodes[0].base-url=http://127.0.0.1:4096",
                        "test-agent.opencode.nodes[0].max-runs=3",
                        "test-agent.opencode.nodes[0].weight=20",
                        "test-agent.opencode.nodes[0].capabilities=chat,diff",
                        "test-agent.opencode.manager-control.token=manager-secret",
                        "test-agent.opencode.manager-control.listen-url=http://10.8.0.21:8080",
                        "test-agent.opencode.manager-control.linux-server-id=10.8.0.21",
                        "test-agent.opencode.manager-control.command-timeout=7s")
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
                    assertThat(properties.getOpencode().getNodes()).hasSize(1);
                    assertThat(properties.getOpencode().getNodes().get(0).getCapabilities())
                            .containsExactly("chat", "diff");
                    assertThat(properties.getOpencode().getManagerControl().getToken()).isEqualTo("manager-secret");
                    assertThat(properties.getOpencode().getManagerControl().getListenUrl()).isEqualTo("http://10.8.0.21:8080");
                    assertThat(properties.getOpencode().getManagerControl().getLinuxServerId()).isEqualTo("10.8.0.21");
                    assertThat(properties.getOpencode().getManagerControl().getCommandTimeout()).isEqualTo(Duration.ofSeconds(7));
                    // gatewayMode 未显式配置时回退为默认 socket，避免空字符串污染网关激活条件。
                    assertThat(properties.getOpencode().getManagerControl().getGatewayMode()).isEqualTo("socket");
                });
    }

    @Test
    void managerControlGatewayModeBindsAndDefaultsAreNormalized() {
        profileContextRunner
                .withPropertyValues(
                        "test-agent.opencode.manager-control.gateway-mode=local")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);
                    assertThat(properties.getOpencode().getManagerControl().getGatewayMode()).isEqualTo("local");
                });
        profileContextRunner
                .withPropertyValues(
                        "test-agent.opencode.manager-control.gateway-mode=  ")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);
                    // 空白字符串会被规整回 socket，避免 @ConditionalOnProperty 走空值不匹配的边界条件。
                    assertThat(properties.getOpencode().getManagerControl().getGatewayMode()).isEqualTo("socket");
                });
    }

    @Test
    void opencodeLocalDirectDefaultsAreFalseAnd1270014096() {
        // 不显式配置时短路默认关闭，baseUrl 回退默认 127.0.0.1:4096，
        // 避免生产环境被误启用导致 topology 校验被跳过。
        contextRunner.run(context -> {
            TestAgentRuntimeProperties.Opencode opencode = context
                    .getBean(TestAgentRuntimeProperties.class)
                    .getOpencode();
            assertThat(opencode.isLocalDirect()).isFalse();
            assertThat(opencode.getLocalDirectBaseUrl()).isEqualTo("http://127.0.0.1:4096");
        });
    }

    @Test
    void opencodeLocalDirectBindsFromPropertiesAndNormalizesBlankBaseUrl() {
        contextRunner
                .withPropertyValues(
                        "test-agent.opencode.local-direct=true",
                        "test-agent.opencode.local-direct-base-url=http://opencode-dev.example.internal:5099")
                .run(context -> {
                    TestAgentRuntimeProperties.Opencode opencode = context
                            .getBean(TestAgentRuntimeProperties.class)
                            .getOpencode();
                    assertThat(opencode.isLocalDirect()).isTrue();
                    assertThat(opencode.getLocalDirectBaseUrl()).isEqualTo("http://opencode-dev.example.internal:5099");
                });
        // baseUrl 为空时回退到默认 127.0.0.1:4096，避免合成进程构造失败。
        contextRunner
                .withPropertyValues(
                        "test-agent.opencode.local-direct=true",
                        "test-agent.opencode.local-direct-base-url=  ")
                .run(context -> {
                    TestAgentRuntimeProperties.Opencode opencode = context
                            .getBean(TestAgentRuntimeProperties.class)
                            .getOpencode();
                    assertThat(opencode.isLocalDirect()).isTrue();
                    assertThat(opencode.getLocalDirectBaseUrl()).isEqualTo("http://127.0.0.1:4096");
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
                        "TEST_AGENT_OPENCODE_NODE_ID=node_test_opencode",
                        "TEST_AGENT_OPENCODE_BASE_URL=http://opencode-test.example.internal:4096",
                        "TEST_AGENT_OPENCODE_MAX_RUNS=6",
                        "TEST_AGENT_OPENCODE_WEIGHT=80",
                        "TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-secret",
                        "TEST_AGENT_BACKEND_LISTEN_URL=http://10.8.0.21:8080",
                        "TEST_AGENT_LINUX_SERVER_ID=10.8.0.21")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

                    assertThat(context.getEnvironment().getProperty("spring.datasource.druid.url"))
                            .isEqualTo("jdbc:postgresql://test-postgres.example.internal:25432/test_agent_ci");
                    assertThat(properties.getOpencode().getNodes()).hasSize(1);
                    TestAgentRuntimeProperties.Node node = properties.getOpencode().getNodes().getFirst();
                    assertThat(node.getId()).isEqualTo("node_test_opencode");
                    assertThat(node.getBaseUrl()).isEqualTo("http://opencode-test.example.internal:4096");
                    assertThat(node.getMaxRuns()).isEqualTo(6);
                    assertThat(node.getWeight()).isEqualTo(80);
                    assertThat(properties.getOpencode().getManagerControl().getToken()).isEqualTo("manager-secret");
                    assertThat(properties.getOpencode().getManagerControl().getListenUrl()).isEqualTo("http://10.8.0.21:8080");
                    assertThat(properties.getOpencode().getManagerControl().getLinuxServerId()).isEqualTo("10.8.0.21");
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
                        "TEST_AGENT_REDIS_ENABLED=true",
                        "TEST_AGENT_REDIS_HOST=prod-redis.example.internal",
                        "TEST_AGENT_REDIS_PORT=6379",
                        "TEST_AGENT_OPENCODE_NODE_ID=node_prod_opencode",
                        "TEST_AGENT_OPENCODE_BASE_URL=http://opencode-prod.example.internal:4096",
                        "TEST_AGENT_OPENCODE_MAX_RUNS=12",
                        "TEST_AGENT_OPENCODE_WEIGHT=100",
                        "TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-secret",
                        "TEST_AGENT_BACKEND_LISTEN_URL=http://10.8.0.22:8080",
                        "TEST_AGENT_LINUX_SERVER_ID=10.8.0.22")
                .run(context -> {
                    TestAgentRuntimeProperties properties = context.getBean(TestAgentRuntimeProperties.class);

                    assertThat(context.getEnvironment().getProperty("spring.datasource.druid.url"))
                            .isEqualTo("jdbc:postgresql://prod-postgres.example.internal:5432/test_agent");
                    assertThat(properties.getSecurity().getApiToken()).isEqualTo("api-token");
                    assertThat(properties.getSecurity().getCorsAllowedOrigins())
                            .containsExactly("https://agent.example.com");
                    assertThat(properties.getRedis().isEnabled()).isTrue();
                    assertThat(properties.getRedis().getHost()).isEqualTo("prod-redis.example.internal");
                    assertThat(properties.getRedis().getPort()).isEqualTo(6379);
                    assertThat(properties.getOpencode().getNodes()).hasSize(1);
                    TestAgentRuntimeProperties.Node node = properties.getOpencode().getNodes().getFirst();
                    assertThat(node.getId()).isEqualTo("node_prod_opencode");
                    assertThat(node.getBaseUrl()).isEqualTo("http://opencode-prod.example.internal:4096");
                    assertThat(node.getMaxRuns()).isEqualTo(12);
                    assertThat(node.getWeight()).isEqualTo(100);
                    assertThat(properties.getOpencode().getManagerControl().getToken()).isEqualTo("manager-secret");
                    assertThat(properties.getOpencode().getManagerControl().getListenUrl()).isEqualTo("http://10.8.0.22:8080");
                    assertThat(properties.getOpencode().getManagerControl().getLinuxServerId()).isEqualTo("10.8.0.22");
                });
    }
}
