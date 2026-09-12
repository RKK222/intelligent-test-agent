package com.enterprise.testagent.integration.codeknowledge;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.integration.codeknowledge.TraceWeaveCodeKnowledgeSettings.RepositoryMapping;
import com.enterprise.testagent.integration.codeknowledge.TraceWeaveCodeKnowledgeSettings.Snapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * TraceWeave 只读适配器。所有检索参数都由服务端版本库映射收窄，资产详情和遍历前再次核对资产归属。
 */
@Service
public class TraceWeaveCodeKnowledgeService {

    private static final int DEFAULT_RESPONSE_BYTES = 10 * 1024 * 1024;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Set<String> OPERATIONS = Set.of("context", "search", "definition", "chain", "impact");

    private final TraceWeaveCodeKnowledgeSettings settings;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Duration requestTimeout;
    private final int maxResponseBytes;

    @Autowired
    public TraceWeaveCodeKnowledgeService(
            TraceWeaveCodeKnowledgeSettings settings,
            ObjectMapper mapper) {
        this(
                settings,
                mapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
                DEFAULT_REQUEST_TIMEOUT,
                DEFAULT_RESPONSE_BYTES);
    }

    TraceWeaveCodeKnowledgeService(
            TraceWeaveCodeKnowledgeSettings settings,
            ObjectMapper mapper,
            HttpClient http,
            Duration requestTimeout,
            int maxResponseBytes) {
        this.settings = Objects.requireNonNull(settings);
        this.mapper = Objects.requireNonNull(mapper);
        this.http = Objects.requireNonNull(http);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
        if (requestTimeout.isZero() || requestTimeout.isNegative() || maxResponseBytes < 1) {
            throw new IllegalArgumentException("request timeout and response limit must be positive");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    /** 执行五种固定只读操作；返回实际选中的 Mimo 版本库 ID，供入口补充源码可用性。 */
    public Result execute(UserId userId, CodeKnowledgeQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        String operation = normalizeOperation(query.operation());
        Snapshot snapshot = settings.require(userId);
        if (!"context".equals(operation) && query.repositoryIds().isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "代码知识查询必须明确选择版本库");
        }
        Scope scope = scope(snapshot, query.repositoryIds());
        JsonNode data = switch (operation) {
            case "context" -> context(snapshot, scope, query.versionKey());
            case "search" -> search(snapshot, scope, query);
            case "definition" -> definition(snapshot, scope, query);
            case "chain", "impact" -> callChains(snapshot, scope, query, operation);
            default -> throw new IllegalStateException("unreachable operation");
        };
        return new Result(data, scope.requestedMappings().stream().map(RepositoryMapping::codeRepositoryId).toList());
    }

    private JsonNode context(Snapshot settings, Scope scope, String versionKey) {
        VersionContext version = resolveVersion(settings, versionKey);
        JsonNode baselines = get(settings, "/api/v1/source-scan-baselines");
        JsonNode tasks = get(settings, "/api/v1/source-scan-tasks?limit=200");
        ObjectNode result = mapper.createObjectNode();
        result.put("operation", "context");
        result.set("repositories", mapper.valueToTree(scope.requestedMappings()));
        result.set("graphEvidence", version.evidence());
        result.set("scanBaselines", filterArray(baselines, scope, "repositoryId", null));
        result.set("scanTasks", filterArray(tasks, scope, "repositoryId", "applicationGroupId"));
        refreshDynamicVersion(settings, version, result);
        return result;
    }

    private JsonNode search(Snapshot settings, Scope scope, CodeKnowledgeQuery query) {
        String text = required(query.query(), "图谱搜索关键字不能为空", 512);
        int limit = bounded(query.limit(), 20, 1, 50, "limit");
        QueryString params = new QueryString().add("q", text).add("limit", Integer.toString(limit));
        scope.requestedMappings().forEach(mapping -> {
            params.add("applicationGroupId", mapping.traceweaveApplicationGroupId());
            params.add("repositoryId", mapping.traceweaveRepositoryId());
        });
        addFilters(params, "assetType", query.assetTypes());
        addFilters(params, "semanticKind", query.semanticKinds());
        addFilters(params, "businessKind", query.businessKinds());
        VersionContext version = resolveVersion(settings, query.versionKey());
        JsonNode response = get(settings, "/api/v1/assets/search?" + params.value());
        requireAssetsInScope(response.path("items"), scope, false);
        ObjectNode result = baseResult("search", scope, version);
        result.put("query", text);
        result.set("data", response);
        result.put("candidateOnly", true);
        refreshDynamicVersion(settings, version, result);
        return result;
    }

    private JsonNode definition(Snapshot settings, Scope scope, CodeKnowledgeQuery query) {
        UUID assetId = assetId(query.assetId());
        JsonNode asset = get(settings, "/api/v1/assets/" + assetId + "?includeDeleted=true");
        requireAssetInScope(asset, scope);
        VersionContext version = resolveVersion(settings, query.versionKey());
        JsonNode response = get(settings, "/api/v1/assets/" + assetId + "/definition?" + version.query());
        requireResponseVersion(response, version);
        ScopedDefinition scoped = scopeDefinitionResponse(response, scope);
        ObjectNode result = baseResult("definition", scope, version);
        result.set("asset", asset);
        result.set("data", scoped.data());
        result.put("definitionScopeFilteredCount", scoped.filteredCount());
        result.put("detailUrl", detailUrl(settings, asset, assetId, version, "chain", false, null, null));
        refreshDynamicVersion(settings, version, result);
        return result;
    }

    private JsonNode callChains(
            Snapshot settings,
            Scope scope,
            CodeKnowledgeQuery query,
            String operation) {
        UUID assetId = assetId(query.assetId());
        JsonNode asset = get(settings, "/api/v1/assets/" + assetId + "?includeDeleted=true");
        requireAssetInScope(asset, scope);
        VersionContext version = resolveVersion(settings, query.versionKey());
        ObjectNode body = mapper.createObjectNode();
        body.putArray("startAssetIds").add(assetId.toString());
        body.put("direction", "chain".equals(operation) ? "BOTH" : "UPSTREAM");
        body.set("version", version.selector());
        ObjectNode filter = body.putObject("filter");
        filter.putArray("assetTypes");
        filter.putArray("relationTypes");
        filter.putArray("relationSources").add("STATIC_ANALYSIS").add("TOOL_IMPORT");
        ArrayNode groups = filter.putArray("applicationGroupIds");
        scope.allowedMappings().stream().map(RepositoryMapping::traceweaveApplicationGroupId)
                .distinct().forEach(groups::add);
        int maxDepth = bounded(query.maxDepth(), 12, 1, 20, "maxDepth");
        int maxNodes = bounded(query.maxNodes(), 1000, 1, 2000, "maxNodes");
        int maxEdges = bounded(query.maxEdges(), 5000, 1, 10000, "maxEdges");
        long timeoutMillis = boundedLong(query.timeoutMillis(), 10000L, 1L, 15000L, "timeoutMillis");
        int maxPaths = bounded(query.maxPaths(), 50, 1, 100, "maxPaths");
        if (timeoutMillis % 1000L != 0L) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "timeoutMillis 必须使用整秒");
        }
        ObjectNode budget = body.putObject("budget");
        budget.put("maxDepth", maxDepth);
        budget.put("maxNodes", maxNodes);
        budget.put("maxEdges", maxEdges);
        budget.put("timeout", Duration.ofMillis(timeoutMillis).toString());
        budget.put("maxPaths", maxPaths);
        body.putNull("cursor");
        ObjectNode traversal = body.putObject("traversalOptions");
        traversal.put("mode", "BUSINESS");
        traversal.put("includeUncertain", Boolean.TRUE.equals(query.includeUncertain()));
        traversal.put("strategy", "PATH_FIRST");
        traversal.put("isolateRepositoryVariants", true);
        ArrayNode repositoryContext = traversal.putArray("repositoryContext");
        scope.allowedMappings().stream().map(RepositoryMapping::traceweaveRepositoryId)
                .distinct().forEach(repositoryContext::add);
        traversal.put("pathContextMode", "PER_PATH");
        traversal.putObject("startContexts");
        traversal.put("entryScope", "SOURCE_FILE".equals(asset.path("assetType").asText())
                ? "FILE_ELEMENTS" : "EXACT");
        traversal.put("conditionMode", "STATIC");
        traversal.put("presentation", "TECHNICAL");
        traversal.put("includeDataFlow", false);
        traversal.put("pathAssembly", "chain".equals(operation) ? "END_TO_END" : "ENTRY_TO_TARGET");
        traversal.put("pathGrouping", "DISTINCT_EVIDENCE_PATH");
        JsonNode response = post(settings, "/api/v1/analysis/call-chains", body);
        requireResponseVersion(response, version);
        requireGraphResponseInScope(response, scope);
        ObjectNode result = baseResult(operation, scope, version);
        result.set("asset", asset);
        result.set("data", response);
        result.set("queryRequest", body.deepCopy());
        result.put("detailUrl", detailUrl(
                settings, asset, assetId, version, operation,
                Boolean.TRUE.equals(query.includeUncertain()), budget, timeoutMillis));
        refreshDynamicVersion(settings, version, result);
        return result;
    }

    private ObjectNode baseResult(String operation, Scope scope, VersionContext version) {
        ObjectNode result = mapper.createObjectNode();
        result.put("operation", operation);
        result.set("repositories", mapper.valueToTree(scope.requestedMappings()));
        result.set("graphEvidence", version.evidence());
        return result;
    }

    private VersionContext resolveVersion(Snapshot settings, String requestedVersionKey) {
        String versionKey = optional(requestedVersionKey, 128);
        String resolutionQuery;
        String view;
        if (versionKey == null) {
            view = settings.defaultView();
            resolutionQuery = "view=" + encode(view);
        } else {
            view = "EXPLICIT_VERSION";
            resolutionQuery = "versionKey=" + encode(versionKey);
        }
        JsonNode resolved = get(settings, "/api/v1/graph-versions/resolve?" + resolutionQuery);
        String resolvedVersionKey = resolved.path("versionKey").asText("").trim();
        if (resolvedVersionKey.isEmpty()) {
            throw unavailable("VERSION_RESOLUTION_INVALID", null);
        }
        ObjectNode selector = mapper.createObjectNode();
        selector.putNull("view");
        selector.put("versionKey", resolvedVersionKey);
        ObjectNode evidence = versionEvidence(resolved, view);
        return new VersionContext(
                selector,
                "versionKey=" + encode(resolvedVersionKey),
                evidence,
                versionKey == null,
                resolutionQuery,
                resolvedVersionKey,
                view);
    }

    /** DEV/PROD 在查询期间移动时保留已固定版本结果，并显式告诉 Agent 当前知识正在更新。 */
    private void refreshDynamicVersion(Snapshot settings, VersionContext version, ObjectNode result) {
        if (!version.dynamicView()) {
            return;
        }
        JsonNode latest = get(settings, "/api/v1/graph-versions/resolve?" + version.resolutionQuery());
        String latestKey = latest.path("versionKey").asText("").trim();
        if (latestKey.isEmpty()) {
            throw unavailable("VERSION_RESOLUTION_INVALID", null);
        }
        boolean updating = !latestKey.equals(version.resolvedVersionKey());
        result.put("knowledgeUpdating", updating);
        if (updating) {
            result.set("latestGraphEvidence", versionEvidence(latest, version.view()));
        }
    }

    private ObjectNode versionEvidence(JsonNode resolved, String view) {
        ObjectNode evidence = resolved.isObject() ? ((ObjectNode) resolved).deepCopy() : mapper.createObjectNode();
        evidence.put("view", view);
        return evidence;
    }

    private void requireResponseVersion(JsonNode response, VersionContext version) {
        String actual = response.path("resolvedVersion").path("versionKey").asText("").trim();
        if (!version.resolvedVersionKey().equals(actual)) {
            throw unavailable("VERSION_RESPONSE_MISMATCH", null);
        }
    }

    private ScopedDefinition scopeDefinitionResponse(JsonNode response, Scope scope) {
        if (!response.isObject() || !response.path("definitions").isArray()) {
            throw unavailable("GRAPH_RESPONSE_INVALID", null);
        }
        requireAssetInScope(response.path("asset"), scope);
        ObjectNode scoped = ((ObjectNode) response).deepCopy();
        ArrayNode definitions = mapper.createArrayNode();
        int filtered = 0;
        for (JsonNode definition : response.path("definitions")) {
            String repositoryId = definition.path("source").path("repositoryId").asText("");
            boolean allowed = repositoryId.isBlank() || scope.allowedMappings().stream()
                    .anyMatch(mapping -> mapping.traceweaveRepositoryId().equals(repositoryId));
            if (allowed) {
                definitions.add(definition);
            } else {
                filtered++;
            }
        }
        scoped.set("definitions", definitions);
        return new ScopedDefinition(scoped, filtered);
    }

    private void requireGraphResponseInScope(JsonNode response, Scope scope) {
        requireAssetsInScope(response.path("nodes"), scope, true);
    }

    private void requireAssetsInScope(JsonNode assets, Scope scope, boolean allowRepositoryless) {
        if (!assets.isArray()) {
            throw unavailable("GRAPH_RESPONSE_INVALID", null);
        }
        for (JsonNode asset : assets) {
            String groupId = asset.path("applicationGroupId").asText("");
            String repositoryId = asset.path("repositoryId").asText("");
            boolean allowed = scope.allowedMappings().stream().anyMatch(mapping ->
                    mapping.traceweaveApplicationGroupId().equals(groupId)
                            && (allowRepositoryless && repositoryId.isBlank()
                                    || mapping.traceweaveRepositoryId().equals(repositoryId)));
            if (!allowed) {
                throw new PlatformException(
                        ErrorCode.FORBIDDEN,
                        "TraceWeave 返回了配置范围外的资产",
                        Map.of("reason", "GRAPH_RESPONSE_OUT_OF_SCOPE"));
            }
        }
    }

    private Scope scope(Snapshot settings, List<String> requestedRepositoryIds) {
        Map<String, RepositoryMapping> byId = new LinkedHashMap<>();
        settings.repositories().forEach(mapping -> byId.put(mapping.codeRepositoryId(), mapping));
        List<String> requested = requestedRepositoryIds == null || requestedRepositoryIds.isEmpty()
                ? List.copyOf(byId.keySet())
                : normalizeRepositoryIds(requestedRepositoryIds);
        List<RepositoryMapping> roots = new ArrayList<>();
        for (String id : requested) {
            RepositoryMapping mapping = byId.get(id);
            if (mapping == null) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "版本库不在代码知识查询范围");
            }
            roots.add(mapping);
        }
        LinkedHashSet<String> allowedIds = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(requested);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (!allowedIds.add(id)) {
                continue;
            }
            pending.addAll(byId.get(id).dependencyRepositoryIds());
        }
        return new Scope(
                List.copyOf(roots),
                allowedIds.stream().map(byId::get).toList());
    }

    private List<String> normalizeRepositoryIds(List<String> values) {
        if (values.size() > 20) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "版本库选择超过上限");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(required(value, "版本库 ID 不能为空", 128)));
        return List.copyOf(result);
    }

    private void requireAssetInScope(JsonNode asset, Scope scope) {
        String repositoryId = asset.path("repositoryId").asText("");
        String applicationGroupId = asset.path("applicationGroupId").asText("");
        boolean allowed = scope.allowedMappings().stream().anyMatch(mapping ->
                mapping.traceweaveRepositoryId().equals(repositoryId)
                        && mapping.traceweaveApplicationGroupId().equals(applicationGroupId));
        if (!allowed) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "资产不在所选版本库范围");
        }
    }

    private ArrayNode filterArray(JsonNode source, Scope scope, String repositoryField, String groupField) {
        ArrayNode result = mapper.createArrayNode();
        if (!source.isArray()) {
            throw unavailable("GRAPH_RESPONSE_INVALID", null);
        }
        for (JsonNode item : source) {
            String repositoryId = item.path(repositoryField).asText("");
            String groupId = groupField == null ? null : item.path(groupField).asText("");
            if (scope.allowedMappings().stream().anyMatch(mapping ->
                    mapping.traceweaveRepositoryId().equals(repositoryId)
                            && (groupId == null || mapping.traceweaveApplicationGroupId().equals(groupId)))) {
                result.add(item);
            }
        }
        return result;
    }

    private String detailUrl(
            Snapshot settings,
            JsonNode asset,
            UUID assetId,
            VersionContext version,
            String operation,
            boolean includeUncertain,
            ObjectNode budget,
            Long timeoutMillis) {
        QueryString params = new QueryString()
                .add("assetId", assetId.toString())
                .add("repositoryId", asset.path("repositoryId").asText())
                .add("applicationGroupId", asset.path("applicationGroupId").asText())
                .add("versionKey", version.evidence().path("versionKey").asText())
                .add("tab", "impact".equals(operation) ? "impact" : "chain")
                .add("entry", "program")
                .add("includeUncertain", Boolean.toString(includeUncertain));
        if (budget != null && timeoutMillis != null) {
            ObjectNode webBudget = mapper.createObjectNode();
            webBudget.put("maxDepth", budget.path("maxDepth").asInt());
            webBudget.put("maxNodes", budget.path("maxNodes").asInt());
            webBudget.put("maxEdges", budget.path("maxEdges").asInt());
            webBudget.put("timeoutSeconds", timeoutMillis / 1000L);
            webBudget.put("maxPaths", budget.path("maxPaths").asInt());
            params.add("budget", webBudget.toString());
        }
        return settings.webBaseUrl() + "/analysis?" + params.value();
    }

    private void addFilters(QueryString params, String name, List<String> values) {
        if (values == null) {
            return;
        }
        if (values.size() > 20) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "图谱过滤项超过上限");
        }
        values.stream().map(value -> required(value, "图谱过滤项不能为空", 128)).distinct()
                .forEach(value -> params.add(name, value));
    }

    private JsonNode get(Snapshot settings, String path) {
        return request(settings, path, null);
    }

    private JsonNode post(Snapshot settings, String path, JsonNode body) {
        return request(settings, path, body);
    }

    private JsonNode request(Snapshot settings, String path, JsonNode body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(settings.serviceBaseUrl() + path))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json");
            if (body == null) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            }
            HttpResponse<InputStream> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                byte[] bytes = input.readNBytes(maxResponseBytes + 1);
                if (bytes.length > maxResponseBytes) {
                    throw unavailable("RESPONSE_TOO_LARGE", response.statusCode());
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw unavailable("UPSTREAM_STATUS", response.statusCode());
                }
                JsonNode parsed = mapper.readTree(bytes);
                if (parsed == null) {
                    throw unavailable("RESPONSE_INVALID", response.statusCode());
                }
                return parsed;
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("INTERRUPTED", null);
        } catch (Exception exception) {
            throw unavailable("NETWORK_OR_RESPONSE", null);
        }
    }

    private PlatformException unavailable(String reason, Integer status) {
        Map<String, Object> details = status == null
                ? Map.of("reason", reason)
                : Map.of("reason", reason, "upstreamStatus", status);
        return new PlatformException(ErrorCode.EXTERNAL_API_UNAVAILABLE, "TraceWeave 代码知识服务暂不可用", details);
    }

    private String normalizeOperation(String value) {
        String operation = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!OPERATIONS.contains(operation)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "不支持的代码知识操作");
        }
        return operation;
    }

    private UUID assetId(String value) {
        try {
            return UUID.fromString(required(value, "资产 ID 不能为空", 64));
        } catch (PlatformException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "资产 ID 无效");
        }
    }

    private String required(String value, String message, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }

    private String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, "版本号无效", maxLength);
    }

    private int bounded(Integer value, int fallback, int min, int max, String field) {
        int actual = value == null ? fallback : value;
        if (actual < min || actual > max) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR, field + " 超出允许范围", Map.of("min", min, "max", max));
        }
        return actual;
    }

    private long boundedLong(Long value, long fallback, long min, long max, String field) {
        long actual = value == null ? fallback : value;
        if (actual < min || actual > max) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR, field + " 超出允许范围", Map.of("min", min, "max", max));
        }
        return actual;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Scope(List<RepositoryMapping> requestedMappings, List<RepositoryMapping> allowedMappings) {
    }

    private record VersionContext(
            ObjectNode selector,
            String query,
            ObjectNode evidence,
            boolean dynamicView,
            String resolutionQuery,
            String resolvedVersionKey,
            String view) {
    }

    private record ScopedDefinition(ObjectNode data, int filteredCount) {
    }

    private static final class QueryString {
        private final List<String> pairs = new ArrayList<>();

        private QueryString add(String name, String value) {
            pairs.add(encode(name) + "=" + encode(value));
            return this;
        }

        private String value() {
            return String.join("&", pairs);
        }
    }

    public record CodeKnowledgeQuery(
            String operation,
            List<String> repositoryIds,
            String query,
            String assetId,
            List<String> assetTypes,
            List<String> semanticKinds,
            List<String> businessKinds,
            Integer limit,
            Boolean includeUncertain,
            String versionKey,
            Integer maxDepth,
            Integer maxNodes,
            Integer maxEdges,
            Long timeoutMillis,
            Integer maxPaths) {
        public CodeKnowledgeQuery {
            repositoryIds = repositoryIds == null ? List.of() : List.copyOf(repositoryIds);
            assetTypes = assetTypes == null ? List.of() : List.copyOf(assetTypes);
            semanticKinds = semanticKinds == null ? List.of() : List.copyOf(semanticKinds);
            businessKinds = businessKinds == null ? List.of() : List.copyOf(businessKinds);
        }
    }

    public record Result(JsonNode data, List<String> repositoryIds) {
        public Result {
            repositoryIds = List.copyOf(repositoryIds);
        }
    }
}
