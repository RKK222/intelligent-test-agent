package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
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

/** 官方风格 Mem0 REST 适配器；日志和异常不包含消息正文、记忆正文或认证密钥。 */
public final class HttpMemoryDocumentStore implements MemoryDocumentStore {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final URI serviceBase;
    private final String serviceApiKey;
    private final Duration timeout;
    private final Duration learningTimeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    HttpMemoryDocumentStore(QaMemoryProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    HttpMemoryDocumentStore(
            QaMemoryProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        Objects.requireNonNull(properties);
        this.serviceBase = validateBaseUri(properties.getServiceUrl());
        this.serviceApiKey = requireApiKey(properties.getServiceApiKey());
        this.timeout = properties.getRequestTimeout();
        this.learningTimeout = properties.getLearningTimeout();
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public List<StoredDocument> add(AddMemories command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", messagePayload(command.messages()));
        body.put("infer", command.infer());
        put(body, "chatModelId", command.chatModelId());
        context(body, command.context());
        owner(body, command.owner());
        body.put("metadata", command.metadata() == null ? Map.of() : command.metadata());
        JsonNode rows = request("POST", "/memories", body, Map.of(), false,
                command.infer() ? learningTimeout : timeout).path("results");
        List<StoredDocument> result = new ArrayList<>();
        rows.forEach(row -> result.add(document(row)));
        return List.copyOf(result);
    }

    @Override
    public StoredDocument update(
            String memoryId,
            String content,
            Map<String, Object> metadata,
            String scope,
            String applicationId,
            RequestContext requestContext) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", content);
        body.put("metadata", metadata == null ? Map.of() : metadata);
        put(body, "scope", scope);
        put(body, "applicationId", applicationId);
        context(body, requestContext);
        return document(request("PUT", "/memories/" + safeId(memoryId), body, Map.of(), false, timeout));
    }

    @Override
    public Optional<StoredDocument> get(String memoryId) {
        JsonNode response = request("GET", "/memories/" + safeId(memoryId), null, Map.of(), true, timeout);
        return response == null ? Optional.empty() : Optional.of(document(response));
    }

    @Override
    public void delete(String memoryId, RequestContext context) {
        Map<String, String> headers = Map.of(
                "X-Memory-Requester-User-Id", context.requesterUserId(),
                "X-Memory-Run-Id", context.runId(),
                "X-Memory-Session-Id", context.sessionId(),
                "X-Memory-Operation-Id", context.operationId());
        request("DELETE", "/memories/" + safeId(memoryId), null, headers, false, timeout);
    }

    @Override
    public List<StoredDocument> search(SearchQuery query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query.query());
        body.put("scopes", query.scopes().stream().map(HttpMemoryDocumentStore::ownerPayload).toList());
        body.put("topK", query.topK());
        body.put("threshold", query.threshold());
        context(body, query.context());
        JsonNode rows = request("POST", "/search", body, Map.of(), false, timeout).path("results");
        List<StoredDocument> result = new ArrayList<>();
        rows.forEach(item -> result.add(document(item)));
        return List.copyOf(result);
    }

    @Override
    public List<HistoryEntry> history(String memoryId) {
        JsonNode rows = request("GET", "/memories/" + safeId(memoryId) + "/history",
                null, Map.of(), false, timeout).path("results");
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
    public Health health() {
        try {
            JsonNode data = request("GET", "/ready", null, Map.of(), false, timeout);
            Map<String, Boolean> availability = new LinkedHashMap<>();
            data.path("profileAvailability").fields().forEachRemaining(
                    entry -> availability.put(entry.getKey(), entry.getValue().asBoolean()));
            List<EmbeddingProfileHealth> profiles = new ArrayList<>();
            data.path("profiles").forEach(profile -> {
                String key = text(profile, "profileKey");
                profiles.add(new EmbeddingProfileHealth(
                        key, text(profile, "provider"), text(profile, "model"),
                        profile.path("dimension").asInt(), text(profile, "fingerprint"),
                        text(profile, "collection"), profile.path("primary").asBoolean(),
                        Boolean.TRUE.equals(availability.get(key))));
            });
            JsonNode backlog = data.path("projectionBacklog");
            return new Health(true, data.path("status").asText("UP"), nullableText(data, "version"),
                    List.copyOf(profiles), new ProjectionBacklog(
                            backlog.path("PENDING").asInt(), backlog.path("PROCESSING").asInt(),
                            backlog.path("DEAD").asInt()));
        } catch (RuntimeException exception) {
            return new Health(false, "DOWN", null);
        }
    }

    private JsonNode request(
            String method,
            String path,
            Map<String, ?> body,
            Map<String, String> extraHeaders,
            boolean allowNotFound,
            Duration requestTimeout) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(serviceBase.resolve(path.substring(1)))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("X-Memory-Service-Key", serviceApiKey);
            extraHeaders.forEach(builder::header);
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
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
        String content = firstText(node, "content", "memory");
        if (!node.isObject() || text(node, "id") == null || content == null) {
            throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "长期记忆服务响应缺少字段");
        }
        Map<String, Object> metadata = objectMapper.convertValue(
                node.path("metadata"), objectMapper.getTypeFactory()
                        .constructMapType(Map.class, String.class, Object.class));
        String updatedAt = firstText(node, "updatedAt", "updated_at");
        return new StoredDocument(text(node, "id"), content,
                metadata == null ? Map.of() : Map.copyOf(metadata),
                updatedAt == null ? Instant.EPOCH : Instant.parse(updatedAt),
                node.hasNonNull("score") ? node.path("score").asDouble() : null);
    }

    private static Object messagePayload(Object messages) {
        if (messages instanceof List<?> values) {
            return values.stream().map(value -> {
                Message message = (Message) value;
                return Map.of("role", message.role(), "content", message.content());
            }).toList();
        }
        return messages;
    }

    private static void context(Map<String, Object> body, RequestContext context) {
        body.put("requesterUserId", context.requesterUserId());
        body.put("runId", context.runId());
        body.put("sessionId", context.sessionId());
        body.put("operationId", context.operationId());
    }

    private static void owner(Map<String, Object> body, OwnerScope owner) {
        body.putAll(ownerPayload(owner));
    }

    private static Map<String, Object> ownerPayload(OwnerScope owner) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scope", owner.scope());
        put(body, "userId", owner.userId());
        put(body, "agentId", owner.agentId());
        put(body, "applicationId", owner.applicationId());
        return body;
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
        if (value == null || !value.matches("[A-Za-z0-9_-]{8,128}")) {
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
}
