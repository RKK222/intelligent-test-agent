package com.enterprise.testagent.opencode.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.observability.TraceConstants;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GeneratedOpencodeSdkGatewayTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private static final String REMOTE_SESSION_ID = "ses_remote1234567890abcdef";
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void observableEventStreamReadyCompletesAfterHeadersBeforeFirstBodyEvent() throws Exception {
        CountDownLatch headersSent = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        HttpServer server = startServer(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().flush();
            headersSent.countDown();
            try {
                releaseBody.await();
                exchange.getResponseBody().write("data: {\"type\":\"server.connected\"}\n\n".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        try {
            OpencodeEventStream opened = new GeneratedOpencodeSdkGateway(List.of())
                    .openEventStream(node(server), "/tmp/demo", null, TRACE_ID);
            CompletableFuture<JsonNode> firstEvent = opened.events().next().toFuture();

            assertThat(headersSent.await(2, TimeUnit.SECONDS)).isTrue();
            opened.ready().block(Duration.ofSeconds(2));
            assertThat(firstEvent).isNotDone();

            releaseBody.countDown();
            assertThat(firstEvent.get(2, TimeUnit.SECONDS).path("type").asText()).isEqualTo("server.connected");
        } finally {
            releaseBody.countDown();
            server.stop(0);
        }
    }

    @Test
    void gatewayCreatesSessionWithoutWorkspaceQueryAndPropagatesTraceId() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", "{\"id\":\"" + REMOTE_SESSION_ID + "\"}");
        });

        try {
            OpencodeCreateSessionResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .createSession(node(server), "/tmp/demo", null, "Demo session", TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.opencodeSessionId()).isEqualTo(REMOTE_SESSION_ID);
            assertThat(request.get().method()).isEqualTo("POST");
            assertThat(request.get().path()).isEqualTo("/api/session");
            assertThat(request.get().query()).doesNotContainKey("directory");
            assertThat(request.get().query()).doesNotContainKey("workspace");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
            assertThat(request.get().body()).contains("\"title\":\"Demo session\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayCreatesSessionWithEmptyRequestBodyWhenTitleIsAbsent() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", "{\"id\":\"" + REMOTE_SESSION_ID + "\"}");
        });

        try {
            new GeneratedOpencodeSdkGateway(List.of())
                    .createSession(node(server), "/tmp/demo", null, null, TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(request.get().body()).contains("\"location\":{\"directory\":\"/tmp/demo\"}");
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
            OpencodeStartRunResult result = new GeneratedOpencodeSdkGateway(List.of())
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
                            null,
                            Map.of("*", false),
                            TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.accepted()).isTrue();
            assertThat(request.get().method()).isEqualTo("POST");
            assertThat(request.get().path()).isEqualTo("/api/session/" + REMOTE_SESSION_ID + "/prompt");
            assertThat(request.get().query()).doesNotContainKey("directory");
            assertThat(request.get().query()).doesNotContainKey("workspace");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
            assertThat(request.get().body()).contains(
                    "\"text\":\"run the tests\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayStartsNativeSessionCommandWithRuntimeSelection() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", "{\"info\":{\"id\":\"msg_1\"},\"parts\":[]}");
        });

        try {
            OpencodeStartRunResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .startCommand(
                            node(server),
                            REMOTE_SESSION_ID,
                            "/tmp/demo",
                            null,
                            "test-design-path",
                            "对车贷的开发文档，生成路径图",
                            List.of(
                                    OpencodePromptPart.text("工作区工具附件只存在于 command arguments"),
                                    OpencodePromptPart.file(
                                            "file:///tmp/demo/.testagent/attachments/sha256_code.java",
                                            "text/plain",
                                            "Demo.java",
                                            Map.of())),
                            null,
                            "build",
                            "opencode",
                            "north-mini-code-free",
                            null,
                            TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.accepted()).isTrue();
            assertThat(request.get().path()).isEqualTo("/api/session/" + REMOTE_SESSION_ID + "/command");
            assertThat(request.get().body()).contains(
                    "\"name\":\"test-design-path\"",
                    "\"text\":\"对车贷的开发文档，生成路径图\"",
                    "\"files\":[",
                    "\"uri\":\"file:///tmp/demo/.testagent/attachments/sha256_code.java\"",
                    "\"name\":\"Demo.java\"")
                    .doesNotContain("工作区工具附件只存在于 command arguments", "\"source\"");
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
            OpencodeStartRunResult result = new GeneratedOpencodeSdkGateway(List.of())
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
                            "plan",
                            "只做只读检查并输出最终答案",
                            "anthropic",
                            "claude-sonnet-4-5",
                            "default",
                            TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.accepted()).isTrue();
            assertThat(request.get().body()).contains(
                    "\"id\":\"msg_remote1234567890abcdef\"",
                    "\"text\":\"review this file\"",
                    "\"files\":[",
                    "\"uri\":\"data:text/plain;base64,ZXhwb3J0IGNvbnN0IGEgPSAx\"",
                    "\"agents\":[",
                    "\"name\":\"plan\"",
                    "\"name\":\"Build\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayOmitsSystemFromPromptWhenAbsent() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respondNoContent(exchange);
        });

        try {
            new GeneratedOpencodeSdkGateway(List.of())
                    .startRun(
                            node(server),
                            REMOTE_SESSION_ID,
                            "/tmp/demo",
                            null,
                            "run the tests",
                            List.of(OpencodePromptPart.text("run the tests")),
                            null,
                            "plan",
                            null,
                            null,
                            null,
                            null,
                            TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(request.get().body()).doesNotContain("\"system\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewaySummarizesPromptPartsWithoutLeakingFileContent() {
        Map<String, Object> request = Map.of(
                "parts", List.of(
                        Map.of("type", "text", "text", "what in this"),
                        Map.of(
                                "type", "file",
                                "mime", "text/plain",
                                "filename", "冲突文件.md",
                                "url", "data:text/plain;base64,5YaF5a65OiDov5nmmK8=",
                                "source", Map.of(
                                        "type", "file",
                                        "path", "99-测试数据/Git冲突处理/冲突文件.md",
                                        "text", Map.of(
                                                "value", "内容：这是远程/应用分支上的修改",
                                                "start", 4,
                                                "end", 21)))));

        List<Map<String, Object>> summary = GeneratedOpencodeSdkGateway.summarizePromptRequest(request);

        assertThat(summary).hasSize(2);
        assertThat(summary.get(0)).containsEntry("type", "text").containsEntry("textChars", 12);
        assertThat(summary.get(1))
                .containsEntry("type", "file")
                .containsEntry("mime", "text/plain")
                .containsEntry("filename", "冲突文件.md");
        assertThat(summary.get(1).get("url"))
                .isInstanceOfSatisfying(Map.class, url -> {
                    Map<?, ?> urlSummary = (Map<?, ?>) url;
                    assertThat(urlSummary.get("scheme")).isEqualTo("data");
                    assertThat(urlSummary.get("dataUrl")).isEqualTo(true);
                    assertThat(urlSummary.get("base64Chars")).isEqualTo(20);
                });
        assertThat(summary.get(1).get("source"))
                .isInstanceOfSatisfying(Map.class, source -> {
                    Map<?, ?> sourceSummary = (Map<?, ?>) source;
                    assertThat(sourceSummary.get("type")).isEqualTo("file");
                    assertThat(sourceSummary.get("path")).isEqualTo("99-测试数据/Git冲突处理/冲突文件.md");
                    assertThat(sourceSummary.get("text"))
                            .isInstanceOfSatisfying(Map.class, text ->
                                    {
                                        Map<?, ?> textSummary = (Map<?, ?>) text;
                                        assertThat(textSummary.get("present")).isEqualTo(true);
                                        assertThat(textSummary.get("chars")).isEqualTo(16);
                                        assertThat(textSummary.get("start")).isEqualTo(4);
                                        assertThat(textSummary.get("end")).isEqualTo(21);
                                    });
                });
        assertThat(summary.toString()).doesNotContain("内容：这是远程/应用分支上的修改", "5YaF5a65OiDov5nmmK8=");
    }

    @Test
    void gatewayAbortsSessionWithoutWorkspaceQuery() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", "true");
        });

        try {
            OpencodeCancelResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .cancelSession(node(server), REMOTE_SESSION_ID, "/tmp/demo", null, TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.cancelled()).isTrue();
            assertThat(request.get().method()).isEqualTo("POST");
            assertThat(request.get().path()).isEqualTo("/api/session/" + REMOTE_SESSION_ID + "/interrupt");
            assertThat(request.get().query()).doesNotContainKey("directory");
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
            JsonNode event = new GeneratedOpencodeSdkGateway(List.of())
                    .streamEvents(node(server), "/tmp/demo", null, TRACE_ID)
                    .blockFirst(Duration.ofSeconds(5));

            assertThat(event.get("type").asText()).isEqualTo("session.next.text.delta");
            assertThat(request.get().method()).isEqualTo("GET");
            assertThat(request.get().path()).isEqualTo("/api/event");
            assertThat(request.get().query()).doesNotContainKey("directory");
            assertThat(request.get().query()).doesNotContainKey("workspace");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayReadsSessionDiffWithOptionalMessageId() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", """
                    [
                      {
                        "file": "src/App.tsx",
                        "patch": "@@ -1 +1 @@\\n-old\\n+new\\n",
                        "additions": 2,
                        "deletions": 1,
                        "status": "modified"
                      }
                    ]
                    """);
        });

        try {
            OpencodeDiffResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .getDiff(
                            node(server),
                            REMOTE_SESSION_ID,
                            "/tmp/demo",
                            "workspace-1",
                            "msg_remote1234567890abcdef",
                            TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.files()).singleElement().satisfies(file -> {
                assertThat(file.path()).isEqualTo("src/App.tsx");
                assertThat(file.patch()).contains("@@");
                assertThat(file.additions()).isEqualTo(2);
                assertThat(file.deletions()).isEqualTo(1);
                assertThat(file.status()).isEqualTo("modified");
            });
            assertThat(request.get().method()).isEqualTo("GET");
            assertThat(request.get().path()).isEqualTo("/api/session/" + REMOTE_SESSION_ID + "/diff");
            assertThat(request.get().query()).doesNotContainKey("directory");
            assertThat(request.get().query()).doesNotContainKey("workspace");
            assertThat(request.get().query()).containsEntry("to", List.of("msg_remote1234567890abcdef"));
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayRejectsDiffWithPartIdUsingStableJsonBody() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respondNoContent(exchange);
        });

        try {
            OpencodeRejectDiffResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .rejectDiff(
                            node(server),
                            REMOTE_SESSION_ID,
                            "/tmp/demo",
                            null,
                            "msg_remote1234567890abcdef",
                            "part_remote1234567890abcdef",
                            TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.rejected()).isTrue();
            assertThat(request.get().method()).isEqualTo("POST");
            assertThat(request.get().path()).isEqualTo("/api/session/" + REMOTE_SESSION_ID + "/revert/stage");
            assertThat(request.get().query()).doesNotContainKey("directory");
            assertThat(request.get().query()).doesNotContainKey("workspace");
            assertThat(request.get().body()).contains("\"messageID\":\"msg_remote1234567890abcdef\"").doesNotContain("partID");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayRunsRuntimeJsonRequestAndFiltersBlankQueryValues() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", "{\"ok\":true}");
        });

        try {
            OpencodeRuntimeResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .runtime(
                            node(server),
                            "POST",
                            "/api/session/" + REMOTE_SESSION_ID + "/permission/req_1/reply",
                            "/tmp/demo",
                            null,
                            Map.of("keep", "yes", "blank", " "),
                            Map.of("decision", "once"),
                            TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.body().path("ok").asBoolean()).isTrue();
            assertThat(request.get().method()).isEqualTo("POST");
            assertThat(request.get().path()).isEqualTo("/api/session/" + REMOTE_SESSION_ID + "/permission/req_1/reply");
            assertThat(request.get().query()).doesNotContainKeys("directory", "location[directory]", "workspace");
            assertThat(request.get().query()).containsEntry("keep", List.of("yes"));
            assertThat(request.get().query()).doesNotContainKey("blank");
            assertThat(request.get().body()).contains("\"decision\":\"once\"");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayRemovesCredentialsFromV2CatalogAndConfigResponses() throws Exception {
        HttpServer server = startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = switch (path) {
                case "/api/model" -> """
                        {"location":{"directory":"/tmp/demo"},"data":[{"id":"safe-model","providerID":"safe-provider","name":"Visible","enabled":true,"capabilities":{"attachment":true},"settings":{"apiKey":"model-secret","timeout":10},"headers":{"Auth-Token":"model-header-secret"},"variants":[{"id":"fast","settings":{"token":"variant-secret"}}]}]}
                        """;
                case "/api/provider" -> """
                        {"location":{"directory":"/tmp/demo"},"data":[{"id":"safe-provider","name":"Visible","activation":"enabled","settings":{"apiKey":"provider-secret"},"headers":{"Authorization":"provider-header-secret"},"body":{"custom":"provider-body-secret"}}]}
                        """;
                case "/api/config" -> """
                        [{"type":"document","info":{"experimental":{"policies":[{"action":"provider.use","resource":"safe-provider","effect":"allow"}]},"providers":{"safe-provider":{"settings":{"apiKey":"config-secret"},"headers":{"Auth-Token":"config-header-secret"}}},"mcp":{"servers":{"docs":{"environment":{"KEY":"mcp-secret"}}}},"plugins":[{"options":{"token":"plugin-secret"}}],"client_secret":"oauth-secret"}}]
                        """;
                default -> "{}";
            };
            respond(exchange, 200, "application/json", body);
        });
        try {
            GeneratedOpencodeSdkGateway gateway = new GeneratedOpencodeSdkGateway(List.of());
            JsonNode models = gateway.runtime(node(server), "GET", "/api/model", "/tmp/demo", null,
                    Map.of(), null, TRACE_ID).block(Duration.ofSeconds(5)).body();
            assertThat(models.path("data").get(0).path("id").asText()).isEqualTo("safe-model");
            assertThat(models.path("data").get(0).path("capabilities").path("attachment").asBoolean()).isTrue();
            assertThat(models.toString()).doesNotContain("model-secret", "model-header-secret", "variant-secret");

            JsonNode providers = gateway.runtime(node(server), "GET", "/api/provider", "/tmp/demo", null,
                    Map.of(), null, TRACE_ID).block(Duration.ofSeconds(5)).body();
            assertThat(providers.path("data").get(0).path("id").asText()).isEqualTo("safe-provider");
            assertThat(providers.toString()).doesNotContain("provider-secret", "provider-header-secret", "provider-body-secret");

            JsonNode config = gateway.runtime(node(server), "GET", "/config", "/tmp/demo", null,
                    Map.of(), null, TRACE_ID).block(Duration.ofSeconds(5)).body();
            assertThat(config.get(0).path("info").path("experimental").path("policies").get(0)
                    .path("resource").asText()).isEqualTo("safe-provider");
            assertThat(config.toString()).doesNotContain("config-secret", "config-header-secret", "mcp-secret",
                    "plugin-secret", "oauth-secret");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayProjectsV2FileReadTextAsLegacyFileContent() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "text/plain", "  hello from v2  \n");
        });

        try {
            JsonNode result = new GeneratedOpencodeSdkGateway(List.of())
                    .runtime(
                            node(server),
                            "GET",
                            "/file/content",
                            "/tmp/demo",
                            null,
                            Map.of("path", "README.md"),
                            null,
                            TRACE_ID)
                    .block(Duration.ofSeconds(5))
                    .body();

            assertThat(result.path("type").asText()).isEqualTo("text");
            assertThat(result.path("content").asText()).isEqualTo("hello from v2");
            assertThat(request.get().method()).isEqualTo("GET");
            assertThat(request.get().path()).isEqualTo("/api/fs/read/README.md");
            assertThat(request.get().query()).containsEntry("location[directory]", List.of("/tmp/demo"));
            assertThat(request.get().query()).doesNotContainKey("path");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayProjectsV2FileReadBinaryAsBase64LegacyFileContent() throws Exception {
        byte[] content = new byte[]{0x00, 0x01, (byte) 0xff, 0x2a};
        HttpServer server = startServer(exchange -> respondBytes(exchange, 200, "application/octet-stream", content));

        try {
            JsonNode result = new GeneratedOpencodeSdkGateway(List.of())
                    .runtime(
                            node(server),
                            "GET",
                            "/file/content",
                            "/tmp/demo",
                            null,
                            Map.of("path", "assets/logo.bin"),
                            null,
                            TRACE_ID)
                    .block(Duration.ofSeconds(5))
                    .body();

            assertThat(result.path("type").asText()).isEqualTo("binary");
            assertThat(result.path("encoding").asText()).isEqualTo("base64");
            assertThat(Base64.getDecoder().decode(result.path("content").asText())).isEqualTo(content);
            assertThat(result.path("mimeType").asText()).isEqualTo("application/octet-stream");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayReadsRegisteredToolsThroughV2PluginRpc() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", """
                    {"output":[{"toolId":"mcp_search","name":"search","description":"Search files","source":"mcp"}]}
                    """);
        });
        try {
            GeneratedOpencodeSdkGateway gateway = new GeneratedOpencodeSdkGateway(List.of());
            OpencodeRuntimeResult catalog = gateway.runtime(node(server), "GET", "/experimental/tool",
                    "/tmp/demo", null, Map.of("provider", "test", "model", "model"), null, TRACE_ID)
                    .block(Duration.ofSeconds(5));
            assertThat(catalog.body().isArray()).isTrue();
            assertThat(catalog.body().get(0).path("toolId").asText()).isEqualTo("mcp_search");
            assertThat(request.get().method()).isEqualTo("POST");
            assertThat(request.get().path()).isEqualTo("/api/rpc/testagent.runtime/tools");
            assertThat(request.get().body()).contains("\"input\":{}");
            assertThat(request.get().query()).containsEntry("location[directory]", List.of("/tmp/demo"));
            assertThat(request.get().query()).doesNotContainKeys("provider", "model");

            OpencodeRuntimeResult ids = gateway.runtime(node(server), "GET", "/experimental/tool/ids",
                    "/tmp/demo", null, Map.of(), null, TRACE_ID).block(Duration.ofSeconds(5));
            assertThat(ids.body().get(0).asText()).isEqualTo("mcp_search");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayProjectsMcpStatusAndResourcesFromV2LocationEnvelopes() throws Exception {
        HttpServer server = startServer(exchange -> {
            String body = exchange.getRequestURI().getPath().endsWith("/resource") ? """
                    {"location":{"directory":"/tmp/demo"},"data":{"resources":[{"server":"docs","name":"readme","uri":"file:///README.md"}],"templates":[{"server":"docs","name":"file","uriTemplate":"file:///{path}"}]}}
                    """ : """
                    {"location":{"directory":"/tmp/demo"},"data":[{"name":"docs","status":{"status":"connected"}},{"name":"broken","status":{"status":"failed","error":"offline"}}]}
                    """;
            respond(exchange, 200, "application/json", body);
        });
        try {
            GeneratedOpencodeSdkGateway gateway = new GeneratedOpencodeSdkGateway(List.of());
            JsonNode status = gateway.runtime(node(server), "GET", "/mcp", "/tmp/demo", null,
                    Map.of(), null, TRACE_ID).block(Duration.ofSeconds(5)).body();
            assertThat(status.path("docs").path("status").asText()).isEqualTo("connected");
            assertThat(status.path("broken").path("error").asText()).isEqualTo("offline");
            assertThat(status.has("location")).isFalse();

            JsonNode resources = gateway.runtime(node(server), "GET", "/experimental/resource", "/tmp/demo", null,
                    Map.of(), null, TRACE_ID).block(Duration.ofSeconds(5)).body();
            assertThat(resources).hasSize(2);
            assertThat(resources.get(0).path("uri").asText()).isEqualTo("file:///README.md");
            assertThat(resources.get(1).path("uri").asText()).isEqualTo("file:///{path}");
            assertThat(resources.get(1).path("type").asText()).isEqualTo("template");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayDoesNotMistakeV2LspConfigForLiveHealth() throws Exception {
        HttpServer server = startServer(exchange -> respond(exchange, 200, "application/json", """
                [{"type":"document","info":{"lsp":false}}]
                """));
        try {
            JsonNode lsp = new GeneratedOpencodeSdkGateway(List.of())
                    .runtime(node(server), "GET", "/lsp", "/tmp/demo", null,
                            Map.of(), null, TRACE_ID).block(Duration.ofSeconds(5)).body();
            assertThat(lsp.path("status").asText()).isEqualTo("disabled");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayCombinesV2VcsBranchAndFileStatus() throws Exception {
        List<String> paths = new ArrayList<>();
        HttpServer server = startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            synchronized (paths) { paths.add(path); }
            respond(exchange, 200, "application/json", path.endsWith("/status") ? """
                    {"location":{"directory":"/tmp/demo"},"data":[{"file":"README.md","status":"modified","additions":1,"deletions":0}]}
                    """ : """
                    {"location":{"directory":"/tmp/demo"},"data":{"provider":"git","branch":{"current":"codex/migration","default":"main"}}}
                    """);
        });
        try {
            JsonNode vcs = new GeneratedOpencodeSdkGateway(List.of())
                    .runtime(node(server), "GET", "/vcs/status", "/tmp/demo", null,
                            Map.of(), null, TRACE_ID).block(Duration.ofSeconds(5)).body();
            assertThat(vcs.path("status").asText()).isEqualTo("ready");
            assertThat(vcs.path("branch").asText()).isEqualTo("codex/migration");
            assertThat(vcs.path("defaultBranch").asText()).isEqualTo("main");
            assertThat(vcs.path("files").get(0).path("file").asText()).isEqualTo("README.md");
            assertThat(paths).containsExactlyInAnyOrder("/api/vcs", "/api/vcs/status");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayReturnsAcceptedRuntimeResultForNoContentResponse() throws Exception {
        HttpServer server = startServer(GeneratedOpencodeSdkGatewayTest::respondNoContent);

        try {
            OpencodeRuntimeResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .runtime(node(server), "POST", "/api/session/" + REMOTE_SESSION_ID + "/abort",
                            "/tmp/demo", null, Map.of(), null, TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.body().path("accepted").asBoolean()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayReadsSessionMessagesUsingGeneratedSessionApi() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            respond(exchange, 200, "application/json", """
                    [
                      {
                        "info": {
                          "id": "msg_remote1234567890abcdef",
                          "sessionID": "ses_remote1234567890abcdef",
                          "role": "assistant",
                          "time": {"created": 1781846400000},
                          "parentID": "msg_user1234567890abcdef",
                          "modelID": "claude-sonnet-4-5",
                          "providerID": "anthropic",
                          "mode": "build",
                          "agent": "build",
                          "path": {"cwd": "/tmp/demo", "root": "/tmp/demo"},
                          "cost": 0,
                          "tokens": {
                            "input": 1,
                            "output": 2,
                            "reasoning": 0,
                            "cache": {"read": 0, "write": 0}
                          }
                        },
                        "parts": [
                          {
                            "id": "part_text_1",
                            "sessionID": "ses_remote1234567890abcdef",
                            "messageID": "msg_remote1234567890abcdef",
                            "type": "text",
                            "text": "hello"
                          },
                          {
                            "id": "part_internal_context",
                            "sessionID": "ses_remote1234567890abcdef",
                            "messageID": "msg_remote1234567890abcdef",
                            "type": "file",
                            "mime": "text/plain",
                            "filename": ".testagent-run-context.txt",
                            "url": "data:text/plain;charset=utf-8;base64,PGNvbnRleHQgLz4="
                          }
                        ]
                      }
                    ]
                    """);
        });

        try {
            OpencodeSessionMessagesResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .sessionMessages(node(server), REMOTE_SESSION_ID, 100, "asc", null, TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.previousCursor()).isNull();
            assertThat(result.nextCursor()).isNull();
            assertThat(result.messages()).singleElement().satisfies(message -> {
                assertThat(message.message()).containsEntry("id", "msg_remote1234567890abcdef");
                assertThat(message.message()).containsEntry("role", "assistant");
                assertThat(message.parts()).singleElement().satisfies(part ->
                        assertThat(part).containsEntry("text", "hello"));
            });
            assertThat(request.get().method()).isEqualTo("GET");
            assertThat(request.get().path()).isEqualTo("/api/session/" + REMOTE_SESSION_ID + "/message");
            assertThat(request.get().query()).containsEntry("limit", List.of("100"));
            assertThat(request.get().query()).doesNotContainKeys("directory", "workspace", "before");
            assertThat(request.get().traceId()).isEqualTo(TRACE_ID);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayReadsLargeSessionMessageSnapshot() throws Exception {
        String largeText = "车贷接口案例".repeat(35_000);
        HttpServer server = startServer(exchange -> respond(exchange, 200, "application/json", """
                [
                  {
                    "info": {
                      "id": "msg_large1234567890abcdef",
                      "sessionID": "ses_remote1234567890abcdef",
                      "role": "assistant",
                      "time": {"created": 1781846400000}
                    },
                    "parts": [
                      {
                        "id": "part_text_large",
                        "messageID": "msg_large1234567890abcdef",
                        "type": "text",
                        "text": "%s"
                      }
                    ]
                  }
                ]
                """.formatted(largeText)));

        try {
            OpencodeSessionMessagesResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .sessionMessages(node(server), REMOTE_SESSION_ID, 100, "asc", null, TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.messages()).singleElement().satisfies(message ->
                    assertThat(message.parts()).singleElement().satisfies(part ->
                            assertThat(part.get("text")).isEqualTo(largeText)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayProjectsV2MessageContentAndBidirectionalCursors() throws Exception {
        HttpServer server = startServer(exchange -> respond(exchange, 200, "application/json", """
                {"data":[
                  {"id":"msg_user1","type":"user","time":{"created":1},"text":"检查状态"},
                  {"id":"msg_assistant1","type":"assistant","time":{"created":2},"agent":"build",
                   "model":{"id":"model1","providerID":"provider1"},
                   "content":[{"type":"text","text":"已完成"},
                              {"type":"tool","id":"call_1","name":"todowrite","time":{"created":2},
                               "state":{"status":"completed","input":{"todos":[{"content":"检查状态","status":"completed"}]},
                                        "content":[{"type":"text","text":"ok"}]}}]}
                ],"cursor":{"previous":"cursor_prev","next":"cursor_next"}}
                """));
        try {
            OpencodeSessionMessagesResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .sessionMessages(node(server), REMOTE_SESSION_ID, 20, "asc", null, TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.previousCursor()).isEqualTo("cursor_prev");
            assertThat(result.nextCursor()).isEqualTo("cursor_next");
            assertThat(result.messages()).hasSize(2);
            assertThat(result.messages().getFirst().message())
                    .containsEntry("role", "user")
                    .containsEntry("sessionID", REMOTE_SESSION_ID);
            assertThat(result.messages().getFirst().parts().getFirst()).containsEntry("text", "检查状态");
            assertThat(result.messages().getLast().message()).containsEntry("role", "assistant");
            assertThat(result.messages().getLast().parts()).hasSize(2);
            assertThat(result.messages().getLast().parts().getLast())
                    .containsEntry("toolName", "todowrite")
                    .containsEntry("callID", "call_1")
                    .containsEntry("sessionID", REMOTE_SESSION_ID)
                    .containsEntry("output", "ok");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayReadsSessionMessagesNextCursorFromHeader() throws Exception {
        AtomicReference<RequestSnapshot> request = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            request.set(snapshot(exchange));
            exchange.getResponseHeaders().set("X-Next-Cursor", "cursor_next_page");
            respond(exchange, 200, "application/json", """
                    [
                      {
                        "info": {
                          "id": "msg_cursor1234567890abcdef",
                          "sessionID": "ses_remote1234567890abcdef",
                          "role": "assistant"
                        },
                        "parts": []
                      }
                    ]
                    """);
        });

        try {
            OpencodeSessionMessagesResult result = new GeneratedOpencodeSdkGateway(List.of())
                    .sessionMessages(node(server), REMOTE_SESSION_ID, 50, "asc", "cursor_previous_page", TRACE_ID)
                    .block(Duration.ofSeconds(5));

            assertThat(result.nextCursor()).isEqualTo("cursor_next_page");
            assertThat(result.messages()).singleElement().satisfies(message ->
                    assertThat(message.message()).containsEntry("id", "msg_cursor1234567890abcdef"));
            assertThat(request.get().query()).containsEntry("limit", List.of("50"));
            assertThat(request.get().query()).containsEntry("cursor", List.of("cursor_previous_page"));
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
        respondBytes(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respondBytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
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
