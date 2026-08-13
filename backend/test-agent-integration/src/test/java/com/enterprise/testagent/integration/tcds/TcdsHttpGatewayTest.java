package com.enterprise.testagent.integration.tcds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.tcds.TcdsGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TcdsHttpGatewayTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void rejectsMissingRelativeAndNonHttpDeploymentAddress() {
        assertThatThrownBy(() -> gateway(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("null");
        assertThatThrownBy(() -> gateway("tcds.internal"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TEST_AGENT_TCDS_BASE_URL 配置无效");
        assertThatThrownBy(() -> gateway("file:///tmp/tcds"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TEST_AGENT_TCDS_BASE_URL 配置无效");
    }

    @Test
    void resolvesAllFixedRequestsUnderOneConfiguredBaseUrl() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        start(exchange -> {
            requests.add(exchange.getRequestURI().toString());
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/loginByUserId")) {
                json(exchange, "{\"code\":\"0\",\"data\":{\"token\":\"secret-token\"}}");
            } else if (path.endsWith("/caseEntrance/getAppAndDeptList")) {
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("secret-token");
                json(exchange, "{\"code\":\"0\",\"data\":{\"app\":[{\"appName\":\"个人金融\",\"appShortName\":\"PSN\"}]}}");
            } else if (path.endsWith("/subItem/getSubItemInfo")) {
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("secret-token");
                json(exchange, "{\"code\":\"0\",\"data\":[{\"itemNo\":\"I-01\",\"itemName\":\"登录需求\",\"children\":[]}]}");
            } else if (path.endsWith("/minio/getTestMinioGraphByItemNo")) {
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("secret-token");
                json(exchange, "{\"code\":\"0\",\"data\":{\"fileName\":\"设计.docx\",\"filePath\":\"https://docs.internal/design\",\"fileType\":\"1\"}}");
            } else if (path.endsWith("/user/getUserByLoginName")) {
                json(exchange, "{\"fullname\":\"张三\",\"loginname\":\"u001\",\"basement\":\"研发\",\"departname\":\"测试\"}");
            } else {
                json(exchange, "{\"code\":\"1\",\"data\":null}");
            }
        });
        TcdsHttpGateway gateway = gateway(baseUrl() + "/gateway/");

        assertThat(gateway.listApplications("u001"))
                .containsExactly(new TcdsGateway.Application("个人金融", "PSN"));
        assertThat(gateway.listRequirementItems("u001", "PSN", "2026年8月"))
                .extracting(TcdsGateway.RequirementItem::itemNo)
                .containsExactly("I-01");
        assertThat(gateway.findFallbackDesignDocument("u001", "SI-01"))
                .get().extracting(TcdsGateway.Document::fileName).isEqualTo("设计.docx");
        assertThat(gateway.findUser("u001"))
                .get().extracting(TcdsGateway.UserProfile::fullName).isEqualTo("张三");
        assertThat(requests).allMatch(path -> path.startsWith("/gateway/"));
        assertThat(requests).noneMatch(path -> path.contains("secret-token"));
    }

    @Test
    void supportsLegacyDirectUserResponseAndEnforcesDocumentProtocolAndSize() throws Exception {
        start(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/user/getUserByLoginName")) {
                json(exchange, "{\"fullname\":\"张三\",\"loginname\":\"u001\",\"basement\":\"研发\",\"departname\":\"测试\"}");
                return;
            }
            byte[] body = "123456".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        TcdsHttpGateway gateway = gateway(baseUrl() + "/");

        assertThat(gateway.findUser("u001")).get().extracting(TcdsGateway.UserProfile::fullName).isEqualTo("张三");
        assertThatThrownBy(() -> gateway.download(
                new TcdsGateway.Document("a.txt", URI.create("ftp://docs.internal/a"), "3"), 20))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> gateway.download(
                new TcdsGateway.Document("a.txt", URI.create(baseUrl() + "/large"), "3"), 5))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @Test
    void followsAtMostThreeHttpRedirects() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        start(exchange -> {
            int redirect = requests.getAndIncrement();
            exchange.getResponseHeaders().set("Location", "/redirect-" + (redirect + 1));
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        TcdsHttpGateway gateway = gateway(baseUrl() + "/");

        assertThatThrownBy(() -> gateway.download(
                new TcdsGateway.Document("a.txt", URI.create(baseUrl() + "/redirect-0"), "3"), 20))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_API_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("TCDS 文档重定向次数过多");
                });
        assertThat(requests).hasValue(4);
    }

    @Test
    void appliesTimeoutsAndMapsTimeoutDetailsToSanitizedFailure() throws Exception {
        assertThat(new TcdsIntegrationConfig().tcdsHttpClient().connectTimeout())
                .contains(Duration.ofSeconds(10));
        HttpClient client = mock(HttpClient.class);
        when(client.send(
                        any(HttpRequest.class),
                        org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenThrow(new HttpTimeoutException("https://docs.internal/a?token=secret"));
        TcdsProperties properties = new TcdsProperties();
        properties.setBaseUrl("https://tcds.internal/");
        TcdsHttpGateway gateway = new TcdsHttpGateway(properties, client, new ObjectMapper());

        assertThatThrownBy(() -> gateway.listApplications("u001"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_API_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("TCDS 服务暂不可用")
                            .doesNotContain("docs.internal", "token", "secret");
                });
    }

    private TcdsHttpGateway gateway(String baseUrl) {
        TcdsProperties properties = new TcdsProperties();
        properties.setBaseUrl(baseUrl);
        return new TcdsHttpGateway(
                properties,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                new ObjectMapper());
    }

    private void start(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception failure) {
                exchange.close();
                throw failure instanceof IOException io ? io : new IOException(failure);
            }
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
