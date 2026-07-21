package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.xxljob.admin.PlatformXxlJobUserProvisioner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.constant.Const;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DefaultXxlJobAdminContextLauncherTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("xxl_job")
            .withUsername("xxl_job")
            .withPassword("test_password");

    @Test
    void startsServletAdminOnIndependentPortAndBlocksNativeLogin() throws Exception {
        XxlJobProperties properties = properties(freePort());
        DefaultXxlJobAdminContextLauncher launcher = new DefaultXxlJobAdminContextLauncher(properties);

        TicketBridge bridge = new TicketBridge();
        try (ConfigurableApplicationContext context = assertDoesNotThrow(() -> TestPropertyValues
                .of("management.endpoint.health.group.readiness.include=readinessState,db,redis")
                .applyToSystemProperties(() -> launcher.launch(bridge)))) {
            assertThat(context).isInstanceOf(ServletWebServerApplicationContext.class);
            assertThat(context.getEnvironment().getProperty("management.endpoint.health.group.readiness.include"))
                    .as("Admin 子上下文不得继承平台 Redis readiness 成员")
                    .isEqualTo("db");
            assertThat(context.getEnvironment().getProperty("xxl.job.timeout"))
                    .as("重定位后的上游默认配置仍应在 Admin 子上下文生效")
                    .isEqualTo("3");
            assertThat(context.getEnvironment().getProperty("spring.freemarker.suffix"))
                    .isEqualTo(".ftl");
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo(MYSQL.getJdbcUrl());
            assertThat(context.getBean(PlatformXxlJobUserProvisioner.class).provision(identity()).username())
                    .isEqualTo("平台管理员");

            HttpResponse<String> readiness = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + properties.getAdmin().getPort()
                                    + "/xxl-job-admin/actuator/health/readiness"))
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(readiness.statusCode()).isEqualTo(200);

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + properties.getAdmin().getPort()
                                    + "/xxl-job-admin/auth/login"))
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.headers().firstValue("Content-Security-Policy"))
                    .contains("frame-ancestors 'self'");
            assertThat(response.headers().firstValue("X-Frame-Options")).contains("SAMEORIGIN");

            HttpResponse<String> login = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()
                    .send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + properties.getAdmin().getPort()
                                            + "/xxl-job-admin/platform-sso/login"))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(HttpRequest.BodyPublishers.ofString("ticket="
                                            + URLEncoder.encode("valid-ticket", StandardCharsets.UTF_8)))
                                    .timeout(Duration.ofSeconds(10))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());

            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(login.body())
                    .contains("status: \"ready\"")
                    .contains("window.location.replace")
                    .contains("xxl-job-admin/")
                    .doesNotContain("valid-ticket");
            String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
            assertThat(setCookie)
                    .contains("test_agent_xxl_login=")
                    .contains("HttpOnly")
                    .contains("Secure")
                    .contains("SameSite=Lax")
                    .contains("Path=/xxl-job-admin/");
            assertThat(bridge.consumed).isTrue();

            try (var connection = DriverManager.getConnection(
                    MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                    var statement = connection.prepareStatement(
                            "SELECT username, role, platform_session_digest FROM xxl_job_user WHERE platform_user_id = ?")) {
                statement.setString(1, "platform-user-1");
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("username")).isEqualTo("平台管理员");
                    assertThat(result.getInt("role")).isEqualTo(1);
                    assertThat(result.getString("platform_session_digest")).isEqualTo("a".repeat(64));
                }
            }
        }
    }

    @Test
    void sharedMysqlKeepsTwoRegistrationsWhenAnotherAdminAddsThirdNode() throws Exception {
        XxlJobEndpointResolver resolver = new XxlJobEndpointResolver();
        XxlJobProperties firstProperties = properties(freePort());
        String first = resolver.resolve(firstProperties, backendIdentity("http://10.23.0.11:8080"))
                .executorAddress();
        String second = resolver.resolve(firstProperties, backendIdentity("http://backend-b.internal:8080"))
                .executorAddress();
        String third = resolver.resolve(firstProperties, backendIdentity("http://10.23.0.13:8080"))
                .executorAddress();

        try (ConfigurableApplicationContext ignored =
                new DefaultXxlJobAdminContextLauncher(firstProperties).launch(new TicketBridge())) {
            clearExecutorRegistrations();
            registerExecutor(firstProperties, first);
            registerExecutor(firstProperties, second);
            awaitExecutorRegistrations(Set.of(first, second));
        }

        // 第二个真实 Admin 复用同一 MySQL；新增节点不要求前两个 executor 重新注册或重启。
        XxlJobProperties secondProperties = properties(freePort());
        try (ConfigurableApplicationContext ignored =
                new DefaultXxlJobAdminContextLauncher(secondProperties).launch(new TicketBridge())) {
            registerExecutor(secondProperties, third);
            awaitExecutorRegistrations(Set.of(first, second, third));
        }
    }

    private static XxlJobProperties properties(int port) {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test_access_token");
        properties.getAdmin().setPort(port);
        properties.getMysql().setUrl(MYSQL.getJdbcUrl());
        properties.getMysql().setUsername(MYSQL.getUsername());
        properties.getMysql().setPassword(MYSQL.getPassword());
        return properties;
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void registerExecutor(XxlJobProperties properties, String executorAddress) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "registryGroup", "EXECUTOR",
                "registryKey", "test-agent-backend",
                "registryValue", executorAddress));
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + properties.getAdmin().getPort()
                                + "/xxl-job-admin/api/registry"))
                        .header(Const.XXL_JOB_ACCESS_TOKEN, properties.getAccessToken())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(10))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private static void awaitExecutorRegistrations(Set<String> expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        Set<String> actual = Set.of();
        while (System.nanoTime() < deadline) {
            actual = executorRegistrations();
            if (actual.containsAll(expected)) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(actual).containsAll(expected);
    }

    private static Set<String> executorRegistrations() throws Exception {
        Set<String> values = new LinkedHashSet<>();
        try (var connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.prepareStatement(
                        "SELECT registry_value FROM xxl_job_registry WHERE registry_group='EXECUTOR' AND registry_key='test-agent-backend'");
                var result = statement.executeQuery()) {
            while (result.next()) {
                values.add(result.getString(1));
            }
        }
        return values;
    }

    private static void clearExecutorRegistrations() throws Exception {
        try (var connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.prepareStatement(
                        "DELETE FROM xxl_job_registry WHERE registry_group='EXECUTOR' AND registry_key='test-agent-backend'")) {
            statement.executeUpdate();
        }
    }

    private static final class TicketBridge implements XxlJobAdminBridge {
        private final AtomicBoolean consumed = new AtomicBoolean();

        @Override
        public Optional<XxlJobSsoIdentity> consumeTicket(String ticket) {
            if (!"valid-ticket".equals(ticket) || !consumed.compareAndSet(false, true)) {
                return Optional.empty();
            }
            return Optional.of(new XxlJobSsoIdentity(
                    identity().platformUserId(),
                    identity().displayName(),
                    identity().sessionDigest(),
                    identity().sessionExpiresAt()));
        }

        @Override
        public boolean isPlatformSessionActive(String sessionDigest) {
            return "a".repeat(64).equals(sessionDigest);
        }
    }

    private static XxlJobSsoIdentity identity() {
        return new XxlJobSsoIdentity(
                "platform-user-1",
                "平台管理员",
                "a".repeat(64),
                Instant.now().plus(Duration.ofMinutes(10)));
    }

    private static BackendInstanceIdentity backendIdentity(String listenUrl) {
        return new BackendInstanceIdentity() {
            @Override
            public String instanceId() {
                return "instance-a";
            }

            @Override
            public String linuxServerId() {
                return "linux-a";
            }

            @Override
            public String backendProcessId() {
                return "bjp_a";
            }

            @Override
            public String listenUrl() {
                return listenUrl;
            }
        };
    }
}
