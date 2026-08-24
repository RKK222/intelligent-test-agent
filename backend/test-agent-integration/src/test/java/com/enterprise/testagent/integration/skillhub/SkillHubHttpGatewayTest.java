package com.enterprise.testagent.integration.skillhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillHubHttpGatewayTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> accessKey = new AtomicReference<>();
    private final AtomicReference<String> downloadQuery = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/list", exchange -> {
            accessKey.set(exchange.getRequestHeaders().getFirst(SkillHubHttpGateway.ACCESS_KEY_HEADER));
            respond(exchange, "application/json", """
                    {"code":0,"msg":"ok","result":[{"id":42,"name":"SLB_ENV_DEEPCHECK","version":"0",
                    "description":"检查测试环境的 SLB 集群 IP","url":"/skill/SLB_ENV_DEEPCHECK.zip",
                    "source":"杭州产品部","tag":null,"sortOrder":null,"status":1,"phase":"04",
                    "phaseName":"测试","contributor":"000831611","createTime":"2026-07-31T02:13:20.000+00:00",
                    "updateTime":"2026-08-11T06:00:21.000+00:00","downloadNum":23,
                    "safetyReportPic":"/skill/temp/pic/report.png","approvalRecord":"[]"}]}
                    """.getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/download/42", exchange -> {
            accessKey.set(exchange.getRequestHeaders().getFirst(SkillHubHttpGateway.ACCESS_KEY_HEADER));
            downloadQuery.set(exchange.getRequestURI().getQuery());
            respond(exchange, "application/zip", new byte[]{1, 2, 3});
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void authenticatesListAndUsesPlatformDownloadChannel() {
        SkillHubProperties properties = new SkillHubProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(baseUrl);
        properties.setAccessKey("test-only-access-key");
        SkillHubHttpGateway gateway = new SkillHubHttpGateway(
                properties, HttpClient.newHttpClient(), new ObjectMapper());

        assertThat(gateway.listSkills()).singleElement().satisfies(skill -> {
            assertThat(skill.id()).isEqualTo(42);
            assertThat(skill.name()).isEqualTo("SLB_ENV_DEEPCHECK");
            assertThat(skill.version()).isEqualTo("0");
            assertThat(skill.source()).isEqualTo("杭州产品部");
            assertThat(skill.phase()).isEqualTo("04");
            assertThat(skill.createdAt()).isEqualTo(java.time.Instant.parse("2026-07-31T02:13:20Z"));
            assertThat(skill.downloadCount()).isEqualTo(23);
        });
        assertThat(gateway.download(42, "0").content()).containsExactly(1, 2, 3);
        assertThat(accessKey).hasValue("test-only-access-key");
        assertThat(downloadQuery).hasValue("channel=" + SkillHubDownloadChannel.PLATFORM.code());
    }

    @Test
    void doesNotContactRemoteEndpointDuringConstruction() {
        SkillHubProperties properties = new SkillHubProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:1/skillhub");
        properties.setAccessKey("test-only-access-key");

        SkillHubHttpGateway gateway = new SkillHubHttpGateway(
                properties, HttpClient.newHttpClient(), new ObjectMapper());

        assertThat(gateway.enabled()).isTrue();
    }

    @Test
    void disabledIntegrationDoesNotRequireEnterpriseConfiguration() {
        SkillHubProperties properties = new SkillHubProperties();

        SkillHubHttpGateway gateway = new SkillHubHttpGateway(
                properties, HttpClient.newHttpClient(), new ObjectMapper());

        assertThat(gateway.enabled()).isFalse();
    }

    private void respond(HttpExchange exchange, String contentType, byte[] body) throws java.io.IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
