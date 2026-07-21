package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.enterprise.testagent.xxljob.admin.PlatformXxlJobUserProvisioner;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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

            try (var connection = java.sql.DriverManager.getConnection(
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
}
