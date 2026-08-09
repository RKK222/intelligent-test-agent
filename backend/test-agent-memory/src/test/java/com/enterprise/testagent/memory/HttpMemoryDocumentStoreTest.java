package com.enterprise.testagent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.memory.QaTaskType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpMemoryDocumentStoreTest {
    private static final String KEY = "memory-service-test-key-000000000000";
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final List<JsonNode> requestBodies = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private HttpMemoryDocumentStore store;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/memory-api/v1", this::handle);
        server.start();
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setEnabled(true);
        properties.setServiceUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setServiceApiKey(KEY);
        properties.setRequestTimeout(Duration.ofSeconds(2));
        store = new HttpMemoryDocumentStore(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void supportsNarrowCrudSearchHistoryAndExtractionContract() {
        MemoryDocumentStore.StoredDocument created = store.add(new MemoryDocumentStore.AddDocument(
                "测试案例必须覆盖边界条件", "platform:user-1", null, "app-1",
                List.of(QaTaskType.TEST_CASE_GENERATION),
                Map.of("scope", "PERSONAL_APPLICATION", "source", "MANUAL")));
        assertThat(created.id()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(store.get(created.id())).isPresent();
        assertThat(store.update(created.id(), "更新后的习惯", Map.of()).content()).contains("边界");
        assertThat(store.search(new MemoryDocumentStore.SearchQuery(
                "如何设计测试", "platform:user-1", null, "app-1",
                "PERSONAL_APPLICATION", 6, 0.1))).hasSize(1);
        assertThat(store.history(created.id())).singleElement()
                .extracting(MemoryDocumentStore.HistoryEntry::event).isEqualTo("ADD");
        assertThat(store.extract(new MemoryDocumentStore.ExtractCommand(
                "internal-chat", "mfg_secret-that-must-never-be-logged", "user-1", "run-1",
                "session-1", "app-1", QaTaskType.DEFECT_ANALYSIS,
                List.of(new MemoryDocumentStore.ExtractionMessage("user", "结论必须有证据")))))
                .singleElement().satisfies(candidate -> {
                    assertThat(candidate.explicit()).isTrue();
                    assertThat(candidate.taskTypes()).containsExactly(QaTaskType.DEFECT_ANALYSIS);
                });
        assertThat(store.health().available()).isTrue();
        store.delete(created.id());

        assertThat(requestBodies).anySatisfy(body -> {
            if (body.has("modelGrant")) {
                assertThat(body.path("modelGrant").asText()).startsWith("mfg_");
            }
        });
    }

    @Test
    void requestRecordsRedactContentAndGrantFromToString() {
        MemoryDocumentStore.ExtractCommand command = new MemoryDocumentStore.ExtractCommand(
                "internal-chat", "mfg_top_secret_value", "user-1", "run-1", "session-1", null,
                QaTaskType.GENERAL,
                List.of(new MemoryDocumentStore.ExtractionMessage("user", "敏感聊天正文")));

        assertThat(command.toString()).doesNotContain("mfg_top_secret_value", "敏感聊天正文");
        assertThat(command.messages().getFirst().toString()).doesNotContain("敏感聊天正文");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (!path.endsWith("/health")) {
                assertThat(exchange.getRequestHeaders().getFirst("X-Memory-Service-Key")).isEqualTo(KEY);
            }
            byte[] request = exchange.getRequestBody().readAllBytes();
            if (request.length > 0) {
                requestBodies.add(objectMapper.readTree(request));
            }
            if ("DELETE".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String response;
            if (path.endsWith("/health")) {
                response = "{\"data\":{\"status\":\"UP\",\"version\":\"0.1.0\"}}";
            } else if (path.endsWith("/history")) {
                response = "{\"data\":[{\"id\":\"h1\",\"memory_id\":\"m1\","
                        + "\"event\":\"ADD\",\"created_at\":\"2026-08-09T00:00:00Z\","
                        + "\"is_deleted\":false}]}";
            } else if (path.endsWith("/search")) {
                response = "{\"data\":{\"items\":[" + documentJson() + "]}}";
            } else if (path.endsWith("/extract")) {
                response = "{\"data\":{\"candidates\":[{"
                        + "\"content\":\"分析结论必须有证据支撑\","
                        + "\"scopeSuggestion\":\"PERSONAL_GLOBAL\","
                        + "\"taskTypes\":[\"DEFECT_ANALYSIS\"],\"explicit\":true,"
                        + "\"temporary\":false,\"replacesExisting\":false,"
                        + "\"confidence\":0.98,\"reason\":\"用户明确要求\"}]}}";
            } else {
                response = "{\"data\":" + documentJson() + "}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(path.endsWith("/documents") ? 201 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static String documentJson() {
        return "{\"id\":\"00000000-0000-0000-0000-000000000001\","
                + "\"content\":\"测试案例必须覆盖边界条件\","
                + "\"metadata\":{\"scope\":\"PERSONAL_APPLICATION\"},"
                + "\"updatedAt\":\"2026-08-09T00:00:00Z\"}";
    }
}
