package com.enterprise.testagent.integration.aam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AamHttpLoginTokenVerifierTest {

    private static final String USER_ID = "AUTH_001";
    private static final String AAM_TOKEN = "secret-aam-token";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsExactCheckLoginContractAndAcceptsOnlyCode200() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        start(exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            json(exchange, 200, "{\"code\":200,\"msg\":null,\"data\":\"登录成功\"}");
        });

        assertThatCode(() -> verifier(baseUrl()).verify(USER_ID, AAM_TOKEN)).doesNotThrowAnyException();

        assertThat(requestPath.get()).isEqualTo("/aam/checkLogin");
        JsonNode payload = new ObjectMapper().readTree(requestBody.get());
        assertThat(payload.fieldNames()).toIterable().containsExactlyInAnyOrder("Token", "userId");
        assertThat(payload.path("Token").asText()).isEqualTo(AAM_TOKEN);
        assertThat(payload.path("userId").asText()).isEqualTo(USER_ID);
    }

    @Test
    void mapsBusinessRejectionAndHttp401Or403ToUnauthenticated() throws Exception {
        start(exchange -> json(exchange, 200, "{\"code\":401,\"data\":\"登录失败\"}"));
        assertFailure(verifier(baseUrl()), ErrorCode.UNAUTHENTICATED);
        stopServer();

        for (int status : new int[]{401, 403}) {
            start(exchange -> json(exchange, status, "{\"code\":200}"));
            assertFailure(verifier(baseUrl()), ErrorCode.UNAUTHENTICATED);
            stopServer();
        }
    }

    @Test
    void mapsUnexpectedStatusMalformedAndOversizedResponsesToUnavailable() throws Exception {
        start(exchange -> json(exchange, 500, "internal url=http://secret?token=value"));
        assertFailure(verifier(baseUrl()), ErrorCode.EXTERNAL_API_UNAVAILABLE);
        stopServer();

        start(exchange -> json(exchange, 200, "not-json-secret-token"));
        assertFailure(verifier(baseUrl()), ErrorCode.EXTERNAL_API_UNAVAILABLE);
        stopServer();

        start(exchange -> json(exchange, 200, "x".repeat(65 * 1024)));
        assertFailure(verifier(baseUrl()), ErrorCode.EXTERNAL_API_UNAVAILABLE);
    }

    @Test
    void mapsTimeoutAndConnectionFailureToSanitizedUnavailableError() throws Exception {
        start(exchange -> {
            Thread.sleep(200);
            json(exchange, 200, "{\"code\":200}");
        });
        AamProperties timeoutProperties = properties(baseUrl());
        timeoutProperties.setRequestTimeout(Duration.ofMillis(30));
        assertFailure(verifier(timeoutProperties), ErrorCode.EXTERNAL_API_UNAVAILABLE);
        stopServer();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String unavailableBaseUrl = baseUrl();
        server.stop(0);
        assertFailure(verifier(unavailableBaseUrl), ErrorCode.EXTERNAL_API_UNAVAILABLE);
    }

    @Test
    void validatesRootHttpOriginAndUsesSecureTimeoutDefaults() {
        for (String invalid : new String[]{
                null,
                "aam.internal",
                "file:///tmp/aam",
                "https://user:pass@aam.internal",
                "https://aam.internal/base",
                "https://aam.internal?token=secret",
                "https://aam.internal#fragment"}) {
            assertThatThrownBy(() -> verifier(invalid))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("TEST_AGENT_AAM_BASE_URL 配置无效")
                    .hasMessageNotContaining("secret")
                    .hasMessageNotContaining("user")
                    .hasMessageNotContaining("pass");
        }

        AamProperties defaults = new AamProperties();
        assertThat(defaults.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(defaults.getRequestTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.getMaxResponseBytes()).isEqualTo(64 * 1024);
        defaults.setBaseUrl("https://aam.internal");
        assertThat(new AamIntegrationConfig().aamHttpClient(defaults).connectTimeout())
                .contains(Duration.ofSeconds(3));
    }

    private void assertFailure(AamHttpLoginTokenVerifier verifier, ErrorCode expectedCode) {
        assertThatThrownBy(() -> verifier.verify(USER_ID, AAM_TOKEN))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(expectedCode);
                    assertThat(exception.getMessage())
                            .doesNotContain(USER_ID, AAM_TOKEN, "http", "登录失败", "not-json");
                });
    }

    private AamHttpLoginTokenVerifier verifier(String baseUrl) {
        return verifier(properties(baseUrl));
    }

    private AamHttpLoginTokenVerifier verifier(AamProperties properties) {
        return new AamHttpLoginTokenVerifier(
                properties,
                HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                new ObjectMapper());
    }

    private AamProperties properties(String baseUrl) {
        AamProperties properties = new AamProperties();
        properties.setBaseUrl(baseUrl);
        return properties;
    }

    private void start(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception failure) {
                exchange.close();
            }
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
