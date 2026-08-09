package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.memory.QaTaskType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** memory-service 窄 HTTP 适配器；响应、请求和异常日志均不得包含正文或授权。 */
public final class HttpMemoryDocumentStore implements MemoryDocumentStore {
    private static final String API_PREFIX = "/memory-api/v1";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final URI serviceBase;
    private final String serviceApiKey;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    HttpMemoryDocumentStore(QaMemoryProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    HttpMemoryDocumentStore(
            QaMemoryProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        Objects.requireNonNull(properties);
        this.serviceBase = validateBaseUri(properties.getServiceUrl());
        this.serviceApiKey = requireApiKey(properties.getServiceApiKey());
        this.timeout = properties.getRequestTimeout();
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public StoredDocument add(AddDocument command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", command.content());
        put(body, "userId", command.userId());
        put(body, "agentId", command.agentId());
        put(body, "applicationId", command.applicationId());
        body.put("taskTypes", command.taskTypes().stream().map(Enum::name).toList());
        body.put("metadata", command.metadata());
        return document(request("POST", "/documents", body, false).path("data"));
    }

    @Override
    public StoredDocument update(String memoryId, String content, Map<String, Object> metadata) {
        JsonNode response = request("PATCH", "/documents/" + safeId(memoryId),
                Map.of("content", content, "metadata", metadata == null ? Map.of() : metadata), false);
        return document(response.path("data"));
    }

    @Override
    public Optional<StoredDocument> get(String memoryId) {
        JsonNode response = request("GET", "/documents/" + safeId(memoryId), null, true);
        return response == null ? Optional.empty() : Optional.of(document(response.path("data")));
    }

    @Override
    public void delete(String memoryId) {
        request("DELETE", "/documents/" + safeId(memoryId), null, false);
    }

    @Override
    public List<StoredDocument> search(SearchQuery query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query.query());
        put(body, "userId", query.userId());
        put(body, "agentId", query.agentId());
        put(body, "applicationId", query.applicationId());
        put(body, "scope", query.scope());
        body.put("topK", query.topK());
        body.put("threshold", query.threshold());
        JsonNode items = request("POST", "/search", body, false).path("data").path("items");
        List<StoredDocument> result = new ArrayList<>();
        items.forEach(item -> result.add(document(item)));
        return List.copyOf(result);
    }

    @Override
    public List<HistoryEntry> history(String memoryId) {
        JsonNode rows = request("GET", "/documents/" + safeId(memoryId) + "/history", null, false)
                .path("data");
        List<HistoryEntry> result = new ArrayList<>();
        rows.forEach(row -> result.add(new HistoryEntry(
                text(row, "id"), firstText(row, "memory_id", "memoryId"),
                firstText(row, "old_memory", "oldMemory"), firstText(row, "new_memory", "newMemory"),
                text(row, "event"), firstInstant(row, "created_at", "createdAt"),
                firstInstant(row, "updated_at", "updatedAt"),
                firstBoolean(row, "is_deleted", "deleted"))));
        return List.copyOf(result);
    }

    @Override
    public List<ExtractedCandidate> extract(ExtractCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", command.model());
        body.put("modelGrant", command.modelGrant());
        body.put("userId", command.userId());
        body.put("runId", command.runId());
        body.put("sessionId", command.sessionId());
        put(body, "applicationId", command.applicationId());
        body.put("taskType", command.taskType().name());
        body.put("messages", command.messages().stream()
                .map(message -> Map.of("role", message.role(), "content", message.content())).toList());
        JsonNode rows = request("POST", "/extract", body, false).path("data").path("candidates");
        List<ExtractedCandidate> result = new ArrayList<>();
        rows.forEach(row -> result.add(new ExtractedCandidate(
                text(row, "content"), text(row, "scopeSuggestion"), taskTypes(row.path("taskTypes")),
                row.path("explicit").asBoolean(), row.path("temporary").asBoolean(),
                row.path("replacesExisting").asBoolean(), row.path("confidence").asDouble(),
                text(row, "reason"))));
        return List.copyOf(result);
    }

    @Override
    public Health health() {
        try {
            JsonNode data = request("GET", "/health", null, false, false).path("data");
            return new Health("UP".equals(data.path("status").asText()),
                    data.path("status").asText("DOWN"), nullableText(data, "version"));
        } catch (RuntimeException exception) {
            return new Health(false, "DOWN", null);
        }
    }

    private JsonNode request(
            String method, String path, Map<String, ?> body, boolean allowNotFound) {
        return request(method, path, body, allowNotFound, true);
    }

    private JsonNode request(
            String method, String path, Map<String, ?> body, boolean allowNotFound, boolean authenticate) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(serviceBase.resolve(API_PREFIX + path))
                    .timeout(timeout)
                    .header("Accept", "application/json");
            if (authenticate) {
                builder.header("X-Memory-Service-Key", serviceApiKey);
            }
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<byte[]> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (allowNotFound && response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "长期记忆服务暂不可用");
            }
            if (response.statusCode() == 204 || response.body().length == 0) {
                return objectMapper.createObjectNode();
            }
            if (response.body().length > MAX_RESPONSE_BYTES) {
                throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "长期记忆服务响应超过安全上限");
            }
            return objectMapper.readTree(response.body());
        } catch (HttpTimeoutException exception) {
            throw new PlatformException(ErrorCode.MEMORY_TIMEOUT, "长期记忆服务请求超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "长期记忆服务请求被中断");
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "长期记忆服务响应格式无效");
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "长期记忆服务暂不可用");
        }
    }

    private StoredDocument document(JsonNode node) {
        if (!node.isObject() || text(node, "id") == null || text(node, "content") == null) {
            throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "长期记忆服务响应缺少字段");
        }
        Map<String, Object> metadata = objectMapper.convertValue(
                node.path("metadata"), objectMapper.getTypeFactory()
                        .constructMapType(Map.class, String.class, Object.class));
        return new StoredDocument(text(node, "id"), text(node, "content"),
                metadata == null ? Map.of() : Map.copyOf(metadata), instant(node, "updatedAt"));
    }

    private static URI validateBaseUri(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("serviceUrl invalid");
            }
            return URI.create(uri.toString().endsWith("/") ? uri.toString() : uri + "/");
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("test-agent.memory.service-url 必须是无凭据的 HTTP origin");
        }
    }

    private static String requireApiKey(String value) {
        if (value == null || value.length() < 32 || value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("启用长期记忆时 service-api-key 至少需要 32 字节");
        }
        return value;
    }

    private static String safeId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9-]{8,80}")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "记忆 ID 格式无效");
        }
        return value;
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String nullableText(JsonNode node, String field) {
        String value = text(node, field);
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value == null ? text(node, second) : value;
    }

    private static Instant firstInstant(JsonNode node, String first, String second) {
        String value = firstText(node, first, second);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static boolean firstBoolean(JsonNode node, String first, String second) {
        JsonNode value = node.get(first);
        return value == null ? node.path(second).asBoolean() : value.asBoolean();
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "长期记忆服务响应缺少时间");
        }
        return Instant.parse(value);
    }

    private static List<QaTaskType> taskTypes(JsonNode node) {
        List<QaTaskType> result = new ArrayList<>();
        node.forEach(item -> result.add(QaTaskType.valueOf(item.asText())));
        return List.copyOf(result);
    }
}
