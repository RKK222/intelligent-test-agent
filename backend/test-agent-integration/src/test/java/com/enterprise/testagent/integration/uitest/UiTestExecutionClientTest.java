package com.enterprise.testagent.integration.uitest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class UiTestExecutionClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void submitForwardsFourColumnsAndKeepsExternalTokenInJava() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/integration/v1/ui-executions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            traceId.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
            body.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 202, successResponse());
        });
        server.start();

        UiTestExecutionClient client = client("integration-secret");
        UiTestExecutionResult result = client.submit(new UiTestExecutionCommand(
                        "session-1:case-1",
                        "登录成功",
                        "1. 打开登录页\n2. 点击登录",
                        "用户名=tester",
                        "进入首页"), "trace_ui_test_123456")
                .block();

        assertThat(authorization).hasValue("Bearer integration-secret");
        assertThat(traceId).hasValue("trace_ui_test_123456");
        assertThat(body.get().get("case_name").asText()).isEqualTo("登录成功");
        assertThat(body.get().get("test_steps").asText()).isEqualTo("1. 打开登录页\n2. 点击登录");
        assertThat(body.get().get("test_data").asText()).isEqualTo("用户名=tester");
        assertThat(body.get().get("expected_result").asText()).isEqualTo("进入首页");
        assertThat(body.get().get("max_steps").asInt()).isEqualTo(25);
        assertThat(result.status()).isEqualTo(UiTestExecutionStatus.RUNNING);
        assertThat(result.reportUrl()).isEqualTo(baseUrl() + "/api/integration/v1/ui-executions/uiexec_abc/report");
    }

    @Test
    void externalAuthenticationFailureUsesStablePlatformError() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/integration/v1/ui-executions", exchange ->
                respond(exchange, 401, "{\"detail\":\"do not leak this\"}"));
        server.start();

        assertThatThrownBy(() -> client("wrong-token").submit(new UiTestExecutionCommand(
                        "session-1:case-1", "", "点击登录", "", ""), "trace_ui_test_123456")
                .block())
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.UI_TEST_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("UI 自动化平台鉴权失败");
                });
    }

    private UiTestExecutionClient client(String token) {
        return new UiTestExecutionClient(
                new UiTestExecutionSettings(baseUrl(), token, 5, 25),
                WebClient.builder().build());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String successResponse() {
        return """
                {
                  "execution_id": "uiexec_abc",
                  "request_id": "session-1:case-1",
                  "case_name": "登录成功",
                  "status": "RUNNING",
                  "success": null,
                  "message": "",
                  "errors": [],
                  "step_count": 0,
                  "duration_seconds": 0.0,
                  "report_url": "/api/integration/v1/ui-executions/uiexec_abc/report",
                  "created_at": "2026-07-31T00:00:00Z",
                  "started_at": "2026-07-31T00:00:01Z",
                  "completed_at": null
                }
                """;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
