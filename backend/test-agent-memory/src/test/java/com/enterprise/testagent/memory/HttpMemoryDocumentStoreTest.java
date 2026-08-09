package com.enterprise.testagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private static final MemoryDocumentStore.RequestContext CONTEXT =
            new MemoryDocumentStore.RequestContext("user-1", "run-1", "session-1", "operation-0001");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final List<JsonNode> requestBodies = new CopyOnWriteArrayList<>();
    private final List<String> requestPaths = new CopyOnWriteArrayList<>();
    private final List<String> requestProtocols = new CopyOnWriteArrayList<>();
    private final List<String> upgradeHeaders = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private HttpMemoryDocumentStore store;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setEnabled(true);
        properties.setServiceUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setServiceApiKey(KEY);
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setLearningTimeout(Duration.ofSeconds(2));
        store = new HttpMemoryDocumentStore(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void supportsOfficialStyleCrudSearchHistoryAndHealthContract() {
        List<MemoryDocumentStore.StoredDocument> learned = store.add(new MemoryDocumentStore.AddMemories(
                List.of(
                        new MemoryDocumentStore.Message("user", "以后输出都使用中文"),
                        new MemoryDocumentStore.Message("assistant", "好的")),
                true,
                "fixed-chat",
                CONTEXT,
                new MemoryDocumentStore.OwnerScope(
                        "PERSONAL_APPLICATION", "platform:user-1", null, "app-1"),
                Map.of("source", "NATIVE")));
        MemoryDocumentStore.StoredDocument created = learned.getFirst();

        assertThat(created.id()).isEqualTo("logical-memory-0001");
        assertThat(store.get(created.id())).isPresent();
        assertThat(store.update(
                created.id(), "更新后的通用偏好", Map.of("source", "MANUAL"),
                "PERSONAL_GLOBAL", null, CONTEXT).content()).isEqualTo("以后输出都使用中文");
        assertThat(store.search(new MemoryDocumentStore.SearchQuery(
                "回答语言", List.of(new MemoryDocumentStore.OwnerScope(
                        "PERSONAL_GLOBAL", "platform:user-1", null, null)), 6, 0.1, CONTEXT)))
                .singleElement().extracting(MemoryDocumentStore.StoredDocument::id)
                .isEqualTo("logical-memory-0001");
        assertThat(store.history(created.id())).singleElement()
                .extracting(MemoryDocumentStore.HistoryEntry::event).isEqualTo("ADD");
        assertThat(store.health()).satisfies(health -> {
            assertThat(health.available()).isTrue();
            assertThat(health.profiles()).singleElement().satisfies(profile -> {
                assertThat(profile.model()).isEqualTo("BAAI/bge-small-zh-v1.5");
                assertThat(profile.dimension()).isEqualTo(512);
                assertThat(profile.available()).isTrue();
            });
            assertThat(health.projectionBacklog().pending()).isEqualTo(2);
        });
        store.delete(created.id(), CONTEXT);

        assertThat(requestPaths).contains(
                "/memories", "/memories/logical-memory-0001", "/search",
                "/memories/logical-memory-0001/history", "/ready");
        assertThat(requestBodies).anySatisfy(body -> {
            if (body.path("infer").asBoolean(false)) {
                assertThat(body.path("chatModelId").asText()).isEqualTo("fixed-chat");
                assertThat(body.path("messages")).hasSize(2);
                assertThat(body.path("scope").asText()).isEqualTo("PERSONAL_APPLICATION");
                assertThat(body.has("prompt")).isFalse();
                assertThat(body.has("customInstructions")).isFalse();
                assertThat(body.has("taskTypes")).isFalse();
                assertThat(body.has("confidence")).isFalse();
                assertThat(body.has("modelGrant")).isFalse();
            }
        });
    }

    @Test
    void requestRecordsDoNotExposeConversationContent() {
        MemoryDocumentStore.Message message = new MemoryDocumentStore.Message("user", "敏感聊天正文");
        MemoryDocumentStore.AddMemories command = new MemoryDocumentStore.AddMemories(
                List.of(message), true, "fixed-chat", CONTEXT,
                new MemoryDocumentStore.OwnerScope(
                        "PERSONAL_APPLICATION", "platform:user-1", null, "app-1"),
                Map.of());

        assertThat(message.toString()).doesNotContain("敏感聊天正文");
        assertThat(command.toString()).doesNotContain("敏感聊天正文");
    }

    @Test
    void forcesHttp11WithoutH2cUpgradeForJsonRequests() {
        store.add(new MemoryDocumentStore.AddMemories(
                "以后输出都使用中文", false, null, CONTEXT,
                new MemoryDocumentStore.OwnerScope(
                        "PERSONAL_GLOBAL", "platform:user-1", null, null),
                Map.of("source", "MANUAL")));

        assertThat(requestProtocols).containsExactly("HTTP/1.1");
        assertThat(upgradeHeaders).containsExactly("");
    }

    @Test
    void enabledConfigurationFailsStartupWithoutHmacSecret() {
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setEnabled(true);
        properties.setServiceApiKey(KEY);

        assertThatThrownBy(() -> new MemoryDocumentStoreConfiguration()
                .memoryDocumentStore(properties, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model-gateway-hmac-secret");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            requestPaths.add(exchange.getRequestURI().getPath());
            requestProtocols.add(exchange.getProtocol());
            upgradeHeaders.add(headerOrEmpty(exchange, "Upgrade"));
            assertThat(exchange.getRequestHeaders().getFirst("X-Memory-Service-Key")).isEqualTo(KEY);
            byte[] request = exchange.getRequestBody().readAllBytes();
            if (request.length > 0) {
                requestBodies.add(objectMapper.readTree(request));
            }
            String path = exchange.getRequestURI().getPath();
            if ("DELETE".equals(exchange.getRequestMethod())) {
                assertThat(headerOrEmpty(exchange, "X-Memory-Operation-Id")).isEqualTo("operation-0001");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String response;
            if (path.equals("/ready")) {
                response = "{\"status\":\"UP\",\"version\":\"2.0.17\","
                        + "\"profiles\":[{\"profileKey\":\"cpu\",\"provider\":\"java-gateway\","
                        + "\"model\":\"BAAI/bge-small-zh-v1.5\",\"dimension\":512,"
                        + "\"fingerprint\":\"revision\",\"collection\":\"cpu-bge\",\"primary\":true}],"
                        + "\"profileAvailability\":{\"cpu\":true},"
                        + "\"projectionBacklog\":{\"PENDING\":2,\"PROCESSING\":1,\"DEAD\":0}}";
            } else if (path.endsWith("/history")) {
                response = "{\"results\":[{\"id\":\"h1\",\"memory_id\":\"logical-memory-0001\","
                        + "\"event\":\"ADD\",\"created_at\":\"2026-08-09T00:00:00Z\","
                        + "\"is_deleted\":false}]}";
            } else if (path.equals("/search") || path.equals("/memories")) {
                response = "{\"results\":[" + documentJson() + "]}";
            } else {
                response = documentJson();
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(path.equals("/memories") ? 201 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static String documentJson() {
        return "{\"id\":\"logical-memory-0001\","
                + "\"content\":\"以后输出都使用中文\","
                + "\"metadata\":{\"scope\":\"PERSONAL_APPLICATION\"},"
                + "\"updatedAt\":\"2026-08-09T00:00:00Z\",\"score\":0.91}";
    }

    private static String headerOrEmpty(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        return value == null ? "" : value;
    }
}
