package com.example.testagent.opencode.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeStatus;
import com.example.testagent.observability.TraceConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GeneratedOpencodeSdkGatewayTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private static final String REMOTE_SESSION_ID = "ses_remote1234567890abcdef";
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void gatewayCreatesSessionWithoutWorkspaceQueryAndPropagatesTraceId() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", "{\"id\":\"" + REMOTE_SESSION_ID + "\"}");
        });

        try {
            OpencodeCreateSessionResult result = new GeneratedOpencodeSdkGateway()
                    .createSession(node(server), "/tmp/demo", null, "Demo session", TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.opencodeSessionId()).isEqualTo(REMOTE_SESSION_ID);
            assertThat(request.get().method()).isEqualTo("POST");
            assertThat(request.get().path()).isEqualTo("/session");
            assertThat(request.get().query()).containsEntry("directory", List.of("/tmp/demo"));
            assertThat(request.get().query()).doesNotContainKey("workspace");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
            assertThat(request.get().body()).contains("\"title\":\"Demo session\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayStartsRunWithoutWorkspaceQueryAndPropagatesTraceId() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respondNoContent(exchange);
        });

        try {
            OpencodeStartRunResult result = new GeneratedOpencodeSdkGateway()
                    .startRun(
                            node(server),
                            REMOTE_SESSION_ID,
                            "/tmp/demo",
                            null,
                            "run the tests",
                            List.of(OpencodePromptPart.text("run the tests")),
                            null,
                            null,
                            null,
                            null,
                            null,
                            TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.accepted()).isTrue();
            assertThat(request.get().method()).isEqualTo("POST");
            assertThat(request.get().path()).isEqualTo("/session/" + REMOTE_SESSION_ID + "/prompt_async");
            assertThat(request.get().query()).containsEntry("directory", List.of("/tmp/demo"));
            assertThat(request.get().query()).doesNotContainKey("workspace");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
            assertThat(request.get().body()).contains("\"type\":\"text\"", "\"text\":\"run the tests\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayStartsRunWithPromptPartsAndRuntimeSelection() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respondNoContent(exchange);
        });

        try {
            OpencodeStartRunResult result = new GeneratedOpencodeSdkGateway()
                    .startRun(
                            node(server),
                            REMOTE_SESSION_ID,
                            "/tmp/demo",
                            null,
                            "review this file",
                            List.of(
                                    OpencodePromptPart.text("review this file"),
                                    OpencodePromptPart.file(
                                            "data:text/plain;base64,ZXhwb3J0IGNvbnN0IGEgPSAx",
                                            "text/plain",
                                            "App.tsx",
                                            Map.of("type", "file", "path", "src/App.tsx",
                                                    "text", Map.of("value", "export const a = 1", "start", 0, "end", 18))),
                                    OpencodePromptPart.agent("Build", Map.of("value", "@build", "start", 0, "end", 6))),
                            "msg_remote1234567890abcdef",
                            "build",
                            "anthropic",
                            "claude-sonnet-4-5",
                            "default",
                            TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.accepted()).isTrue();
            assertThat(request.get().body()).contains(
                    "\"messageID\":\"msg_remote1234567890abcdef\"",
                    "\"agent\":\"build\"",
                    "\"providerID\":\"anthropic\"",
                    "\"modelID\":\"claude-sonnet-4-5\"",
                    "\"variant\":\"default\"",
                    "\"type\":\"text\"",
                    "\"type\":\"file\"",
                    "\"url\":\"data:text/plain;base64,ZXhwb3J0IGNvbnN0IGEgPSAx\"",
                    "\"type\":\"agent\"",
                    "\"name\":\"Build\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayAbortsSessionWithoutWorkspaceQuery() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", "true");
        });

        try {
            OpencodeCancelResult result = new GeneratedOpencodeSdkGateway()
                    .cancelSession(node(server), REMOTE_SESSION_ID, "/tmp/demo", null, TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.cancelled()).isTrue();
            assertThat(request.get().method()).isEqualTo("POST");
            assertThat(request.get().path()).isEqualTo("/session/" + REMOTE_SESSION_ID + "/abort");
            assertThat(request.get().query()).containsEntry("directory", List.of("/tmp/demo"));
            assertThat(request.get().query()).doesNotContainKey("workspace");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayStreamsEventsWithoutWorkspaceQuery() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "text/event-stream", """
                    data: {"id":"evt_raw_1","type":"session.next.text.delta","properties":{"text":"hello"}}

                    """);
        });

        try {
            JsonNode event = new GeneratedOpencodeSdkGateway()
                    .streamEvents(node(server), "/tmp/demo", null, TRACE_ID)
                    .blockFirst(Duration.ofSeconds(5));

            assertThat(event.get("type").asText()).isEqualTo("session.next.text.delta");
            assertThat(request.get().method()).isEqualTo("GET");
            assertThat(request.get().path()).isEqualTo("/event");
            assertThat(request.get().query()).containsEntry("directory", List.of("/tmp/demo"));
            assertThat(request.get().query()).doesNotContainKey("workspace");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayReadsSessionMessagesUsingGeneratedMessagesApi() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", """
                    {
                      "data": [
                        {
                          "id": "msg_remote1234567890abcdef",
                          "type": "assistant",
                          "agent": "build",
                          "model": {"id": "claude-sonnet-4-5", "providerID": "anthropic"},
                          "content": [
                            {"type": "text", "id": "part_text_1", "text": "hello"}
                          ],
                          "time": {"created": 1781846400000}
                        }
                      ],
                      "cursor": {"previous": "previous_cursor", "next": "next_cursor"}
                    }
                    """);
        });

        try {
            OpencodeSessionMessagesResult result = new GeneratedOpencodeSdkGateway()
                    .sessionMessages(node(server), REMOTE_SESSION_ID, 100, "asc", null, TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.previousCursor()).isEqualTo("previous_cursor");
            assertThat(result.nextCursor()).isEqualTo("next_cursor");
            assertThat(result.messages()).singleElement().satisfies(message -> {
                assertThat(message.message()).containsEntry("id", "msg_remote1234567890abcdef");
                assertThat(message.parts()).singleElement().satisfies(part ->
                        assertThat(part).containsEntry("text", "hello"));
            });
            assertThat(request.get().method()).isEqualTo("GET");
            assertThat(request.get().path()).isEqualTo("/api/session/" + REMOTE_SESSION_ID + "/message");
            assertThat(request.get().query()).containsEntry("limit", List.of("100"));
            assertThat(request.get().query()).containsEntry("order", List.of("asc"));
            assertThat(request.get().query()).doesNotContainKey("cursor");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private static ExecutionNode node(HttpServer server) {
        return new ExecutionNode(
                new ExecutionNodeId("node_1234567890abcdef"),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                ExecutionNodeStatus.READY,
                0,
                4,
                100,
                NOW,
                Set.of("chat"),
                NOW,
                NOW,
                TRACE_ID);
    }

    private static RequestSnapshot snapshot(HttpExchange exchange) throws IOException {
        return new RequestSnapshot(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                query(exchange.getRequestURI().getRawQuery()),
                exchange.getRequestHeaders().getFirst(TraceConstants.TRACE_ID_HEADER),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static Map<String, List<String>> query(String rawQuery) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String name = decode(parts[0]);
            String value = parts.length == 2 ? decode(parts[1]) : "";
            result.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void respondNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private record RequestSnapshot(
            String method,
            String path,
            Map<String, List<String>> query,
            String traceId,
            String body) {
    }
}
