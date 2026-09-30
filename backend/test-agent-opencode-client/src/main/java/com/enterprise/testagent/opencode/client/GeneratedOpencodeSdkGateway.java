package com.enterprise.testagent.opencode.client;

import com.example.opencode.sdk.ApiClient;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.observability.TraceConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * generated SDK 的唯一直接调用点；本类之外不应 import com.example.opencode.sdk.*。
 */
@Component
public class GeneratedOpencodeSdkGateway implements OpencodeSdkGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratedOpencodeSdkGateway.class);
    private static final int OPENCODE_RESPONSE_MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024;
    private static final Pattern PROVIDER_OAUTH_CALLBACK =
            Pattern.compile("^/provider/([^/]+)/oauth/callback$");
    private static final Set<String> PRIVATE_CATALOG_FIELDS = Set.of(
            "apikey", "token", "authtoken", "accesstoken", "refreshtoken", "localtoken",
            "password", "passphrase", "secret", "clientsecret", "privatekey", "credential",
            "credentials", "authorization", "cookie", "setcookie", "proxyauthorization",
            "headers", "settings", "body", "environment", "env", "options", "auth", "oauth",
            "plugins", "mcp");
    private static final Set<String> CATALOG_RESPONSE_PATHS = Set.of(
            "/api/model", "/model", "/api/provider", "/provider", "/api/config",
            "/config", "/global/config", "/api/agent", "/agent", "/api/command",
            "/command", "/api/reference", "/reference", "/api/integration", "/provider/auth");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<OpencodeWebClientTransport> transports;

    /**
     * 生产组件必须显式注入可用传输；不保留无参构造器，避免本地节点
     * 静默回退到对 local-opencode-client.invalid 的真实网络请求。
     */
    public GeneratedOpencodeSdkGateway(List<OpencodeWebClientTransport> transports) {
        this.transports = transports == null ? List.of() : List.copyOf(transports);
    }

    /**
     * 调用 opencode health API，并把 generated 响应归一为平台健康结果。
     */
    @Override
    public Mono<OpencodeHealthResult> health(ExecutionNode node, String traceId) {
        return invokeJson(node, "GET", "/api/info", Map.of(), null, Map.of(), traceId)
                .map(ignored -> new OpencodeHealthResult(true, node.baseUrl()));
    }

    /**
     * 创建 opencode session；generated SDK 缺少稳定 DTO 时直接使用 JsonNode 提取 session id。
     */
    @Override
    public Mono<OpencodeCreateSessionResult> createSession(
            ExecutionNode node,
            String directory,
            String workspace,
            String title,
            String traceId) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        if (optionalText(title) != null) request.put("title", title);
        request.put("location", Map.of("directory", directory));
        return invokeJson(node, "POST", "/api/session", Map.of(), request, Map.of(), traceId)
                .map(body -> new OpencodeCreateSessionResult(extractSessionId(body)));
    }

    /**
     * 通过 v2 session get 判断远端 session 是否存在；404 由 facade 统一转换为 false。
     */
    @Override
    public Mono<Boolean> sessionExists(ExecutionNode node, String opencodeSessionId, String traceId) {
        return invokeJson(node, "GET", "/api/session/" + opencodeSessionId, Map.of(), null, Map.of(), traceId)
                .thenReturn(true);
    }

    /**
     * 调用远端 session abort，workspace 仅在非空时进入 query。
     */
    @Override
    public Mono<OpencodeCancelResult> cancelSession(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String traceId) {
        return invokeJson(
                        node,
                        "POST",
                        "/api/session/" + opencodeSessionId + "/interrupt",
                        Map.of(),
                        null,
                        Map.of("resume", "false"),
                        traceId)
                .map(cancelled -> new OpencodeCancelResult(true));
    }

    /**
     * 通过 V2 prompt 请求启动 opencode 运行，使用稳定 JSON Map 隔离 generated union DTO 差异。
     */
    @Override
    public Mono<OpencodeStartRunResult> startRun(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String prompt,
            List<OpencodePromptPart> parts,
            String messageId,
            String agent,
            String system,
            String modelProviderId,
            String modelId,
            String variant,
            Map<String, Boolean> tools,
            String traceId) {
        // V2 prompt 将文本、文件和 agent 引用拆成独立字段；这里集中完成平台 part 转换。
        Map<String, Object> request = promptRequest(
                parts, messageId, agent, system, modelProviderId, modelId, variant, tools, prompt);
        logPromptRequestPrepared(
                node,
                opencodeSessionId,
                directory,
                workspace,
                messageId,
                agent,
                modelProviderId,
                modelId,
                variant,
                traceId,
                request);
        return selectSessionRuntime(node, opencodeSessionId, agent, modelProviderId, modelId, variant, traceId)
                .then(invokeJson(
                        node,
                        "POST",
                        "/api/session/" + opencodeSessionId + "/prompt",
                        Map.of(),
                        request,
                        Map.of(),
                        traceId))
                .doOnSuccess(ignored -> logPromptRequestAccepted(
                        node,
                        opencodeSessionId,
                        messageId,
                        traceId,
                        request))
                .thenReturn(new OpencodeStartRunResult(true));
    }

    /** 兼容未声明工具开关的内部测试和旧调用点。 */
    public Mono<OpencodeStartRunResult> startRun(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String prompt,
            List<OpencodePromptPart> parts,
            String messageId,
            String agent,
            String system,
            String modelProviderId,
            String modelId,
            String variant,
            String traceId) {
        return startRun(
                node,
                opencodeSessionId,
                directory,
                workspace,
                prompt,
                parts,
                messageId,
                agent,
                system,
                modelProviderId,
                modelId,
                variant,
                Map.of(),
                traceId);
    }

    /**
     * 调用同步 session command；平台会在创建 Run 和订阅事件后于后台订阅本请求。
     */
    @Override
    public Mono<OpencodeStartRunResult> startCommand(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String command,
            String arguments,
            List<OpencodePromptPart> parts,
            String messageId,
            String agent,
            String modelProviderId,
            String modelId,
            String variant,
            String traceId) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("name", command);
        request.put("text", arguments == null ? "" : arguments);
        String optionalAgent = optionalText(agent);
        if (optionalAgent != null) {
            request.put("agents", List.of(Map.of("name", optionalAgent)));
        }
        List<Map<String, Object>> fileParts = parts == null
                ? List.of()
                : parts.stream()
                        .filter(part -> "file".equals(part.type()))
                        .map(part -> {
                            Map<String, Object> file = new LinkedHashMap<>();
                            file.put("uri", part.url());
                            file.put("name", part.filename() == null ? "attachment" : part.filename());
                            return file;
                        })
                        .toList();
        if (!fileParts.isEmpty()) {
            request.put("files", fileParts);
        }
        ApiClient apiClient = apiClient(node, traceId);
        ParameterizedTypeReference<JsonNode> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("sessionID", opencodeSessionId);
        // V2 session 已绑定创建时的 location，固定上下文操作不再重复发送目录 query。
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        HttpHeaders headerParams = new HttpHeaders();
        MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
        MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();
        List<MediaType> accepts = apiClient.selectHeaderAccept(new String[]{"application/json"});
        MediaType contentType = apiClient.selectHeaderContentType(new String[]{"application/json"});
        return selectSessionRuntime(node, opencodeSessionId, agent, modelProviderId, modelId, variant, traceId)
                .then(apiClient.invokeAPI(
                        "/api/session/" + opencodeSessionId + "/command",
                        HttpMethod.POST,
                        pathParams,
                        queryParams,
                        Map.copyOf(request),
                        headerParams,
                        cookieParams,
                        formParams,
                        accepts,
                        contentType,
                        new String[]{},
                        returnType).bodyToMono(returnType))
                .thenReturn(new OpencodeStartRunResult(true));
    }

    /** V2 prompt/command 不接受旧 model/agent 字段，先在固定 session 上选择运行上下文。 */
    private Mono<Void> selectSessionRuntime(
            ExecutionNode node,
            String sessionId,
            String agent,
            String providerId,
            String modelId,
            String variant,
            String traceId) {
        Mono<Void> selected = Mono.empty();
        if (optionalText(agent) != null) {
            selected = selected.then(invokeJson(node, "POST", "/api/session/" + sessionId + "/agent",
                    Map.of(), Map.of("agent", agent), Map.of(), traceId)).then();
        }
        if (optionalText(providerId) != null && optionalText(modelId) != null) {
            LinkedHashMap<String, Object> model = new LinkedHashMap<>();
            model.put("providerID", providerId);
            model.put("id", modelId);
            if (optionalText(variant) != null) model.put("variant", variant);
            selected = selected.then(invokeJson(node, "POST", "/api/session/" + sessionId + "/model",
                    Map.of(), Map.of("model", model), Map.of(), traceId)).then();
        }
        return selected;
    }

    /** 构造 V2 prompt 请求体，保留平台附件和 agent 语义。 */
    private Map<String, Object> promptRequest(
            List<OpencodePromptPart> parts,
            String messageId,
            String agent,
            String system,
            String modelProviderId,
            String modelId,
            String variant,
            Map<String, Boolean> tools,
            String prompt) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        String optionalMessageId = optionalText(messageId);
        if (optionalMessageId != null) request.put("id", optionalMessageId);
        String text = (parts == null || parts.isEmpty())
                ? prompt
                : parts.stream().filter(part -> "text".equals(part.type())).map(OpencodePromptPart::text).reduce("", (a, b) -> a + b);
        request.put("text", text == null ? "" : text);
        List<Map<String, Object>> files = parts == null ? List.of() : parts.stream()
                .filter(part -> "file".equals(part.type()))
                .map(part -> {
                    LinkedHashMap<String, Object> file = new LinkedHashMap<>();
                    file.put("uri", part.url());
                    if (optionalText(part.filename()) != null) file.put("name", part.filename());
                    return Map.copyOf(file);
                }).toList();
        if (!files.isEmpty()) request.put("files", files);
        List<Map<String, Object>> agents = new ArrayList<>();
        if (optionalText(agent) != null) agents.add(Map.of("name", agent));
        if (parts != null) parts.stream().filter(part -> "agent".equals(part.type()))
                .forEach(part -> agents.add(Map.of("name", part.name())));
        if (!agents.isEmpty()) request.put("agents", agents);
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        if (optionalText(system) != null) metadata.put("system", system);
        if (optionalText(modelProviderId) != null && optionalText(modelId) != null) {
            metadata.put("model", modelProviderId + "/" + modelId);
        }
        if (optionalText(variant) != null) metadata.put("variant", variant);
        if (tools != null && !tools.isEmpty()) metadata.put("tools", Map.copyOf(tools));
        if (!metadata.isEmpty()) request.put("metadata", metadata);
        return Map.copyOf(request);
    }

    /**
     * 记录发给 opencode prompt 的结构摘要，避免正文、data URL 和 source.text 原文进入日志。
     */
    private void logPromptRequestPrepared(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String messageId,
            String agent,
            String modelProviderId,
            String modelId,
            String variant,
            String traceId,
            Map<String, Object> request) {
        LOGGER.info(
                "opencode_prompt_request_prepared traceId={} nodeId={} baseUrl={} sessionId={} directoryPresent={} workspacePresent={} messageId={} agent={} modelProviderId={} modelId={} variant={} partsCount={} partsSummary={}",
                traceId,
                node.executionNodeId().value(),
                node.baseUrl(),
                opencodeSessionId,
                optionalText(directory) != null,
                optionalText(workspace) != null,
                optionalText(messageId),
                optionalText(agent),
                optionalText(modelProviderId),
                optionalText(modelId),
                optionalText(variant),
                promptParts(request).size(),
                summarizePromptRequest(request));
    }

    /**
     * 记录 opencode 已接受 prompt 请求，便于和后续 global/event 中的 user message parts 对照。
     */
    private void logPromptRequestAccepted(
            ExecutionNode node,
            String opencodeSessionId,
            String messageId,
            String traceId,
            Map<String, Object> request) {
        LOGGER.info(
                "opencode_prompt_request_accepted traceId={} nodeId={} baseUrl={} sessionId={} messageId={} partsCount={} partsSummary={}",
                traceId,
                node.executionNodeId().value(),
                node.baseUrl(),
                opencodeSessionId,
                optionalText(messageId),
                promptParts(request).size(),
                summarizePromptRequest(request));
    }

    /**
     * 提取 prompt parts 的脱敏摘要，保留 type/mime/filename/source 范围用于核对原生附件形态。
     */
    static List<Map<String, Object>> summarizePromptRequest(Map<String, Object> request) {
        return promptParts(request).stream()
                .map(GeneratedOpencodeSdkGateway::summarizePromptPart)
                .toList();
    }

    private static List<Map<String, Object>> promptParts(Map<String, Object> request) {
        Object rawParts = request == null ? null : request.get("parts");
        if (!(rawParts instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        for (Object rawPart : list) {
            if (rawPart instanceof Map<?, ?> part) {
                parts.add(copyStringObjectMap(part));
            }
        }
        return List.copyOf(parts);
    }

    private static Map<String, Object> summarizePromptPart(Map<String, Object> part) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        String type = promptSummaryStringValue(part.get("type"));
        putIfPresent(summary, "type", type);
        if ("text".equals(type)) {
            putIfPresent(summary, "textChars", textLength(part.get("text")));
        } else if ("file".equals(type)) {
            putIfPresent(summary, "mime", promptSummaryStringValue(part.get("mime")));
            putIfPresent(summary, "filename", promptSummaryStringValue(part.get("filename")));
            summary.put("url", summarizeUrl(part.get("url")));
            summary.put("source", summarizeSource(part.get("source")));
        } else if ("agent".equals(type)) {
            putIfPresent(summary, "name", promptSummaryStringValue(part.get("name")));
            summary.put("source", summarizeSource(part.get("source")));
        }
        return Map.copyOf(summary);
    }

    private static Map<String, Object> summarizeUrl(Object rawUrl) {
        String url = promptSummaryStringValue(rawUrl);
        if (url == null) {
            return Map.of("present", false);
        }
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("present", true);
        summary.put("chars", url.length());
        int schemeEnd = url.indexOf(':');
        String scheme = schemeEnd > 0 ? url.substring(0, schemeEnd) : "unknown";
        summary.put("scheme", scheme);
        summary.put("dataUrl", url.startsWith("data:"));
        int base64Start = url.indexOf(";base64,");
        if (base64Start >= 0) {
            summary.put("base64Chars", url.length() - base64Start - ";base64,".length());
        }
        return Map.copyOf(summary);
    }

    private static Map<String, Object> summarizeSource(Object rawSource) {
        if (!(rawSource instanceof Map<?, ?> source)) {
            return Map.of("present", false);
        }
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("present", true);
        putIfPresent(summary, "type", promptSummaryStringValue(source.get("type")));
        putIfPresent(summary, "path", promptSummaryStringValue(source.get("path")));
        Object rawText = source.get("text");
        if (rawText instanceof Map<?, ?> textMap) {
            LinkedHashMap<String, Object> textSummary = new LinkedHashMap<>();
            textSummary.put("present", true);
            putIfPresent(textSummary, "chars", textLength(textMap.get("value")));
            putIfPresent(textSummary, "start", numberValue(textMap.get("start")));
            putIfPresent(textSummary, "end", numberValue(textMap.get("end")));
            summary.put("text", Map.copyOf(textSummary));
        } else if (rawText instanceof String text) {
            summary.put("text", Map.of("present", true, "chars", text.length()));
        } else {
            summary.put("text", Map.of("present", false));
        }
        return Map.copyOf(summary);
    }

    private static Map<String, Object> copyStringObjectMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key instanceof String name) {
                copy.put(name, value);
            }
        });
        return Map.copyOf(copy);
    }

    private static String promptSummaryStringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private static Integer textLength(Object value) {
        return value instanceof String string ? string.length() : null;
    }

    private static Object numberValue(Object value) {
        return value instanceof Number number ? number : null;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /**
     * 订阅 opencode SSE 原始事件流，由 facade 再映射为平台 RunEventDraft。
     */
    @Override
    public Flux<JsonNode> streamEvents(ExecutionNode node, String directory, String workspace, String traceId) {
        return invokeEventStream(node, traceId);
    }

    /**
     * 通过 toEntityFlux 把 HTTP 响应头与 SSE body 分开；response Mono 完成即代表服务端已接受订阅并返回流式响应。
     */
    @Override
    public OpencodeEventStream openEventStream(
            ExecutionNode node,
            String directory,
            String workspace,
            String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        ParameterizedTypeReference<JsonNode> returnType = new ParameterizedTypeReference<>() {};
        Mono<ResponseEntity<Flux<JsonNode>>> response = apiClient.invokeAPI(
                        "/api/event", HttpMethod.GET, Map.of(), new LinkedMultiValueMap<>(), null,
                        new HttpHeaders(), new LinkedMultiValueMap<>(), new LinkedMultiValueMap<>(),
                        List.of(MediaType.TEXT_EVENT_STREAM), MediaType.APPLICATION_JSON, new String[]{}, returnType)
                .toEntityFlux(JsonNode.class)
                .cache();
        return new OpencodeEventStream(
                response.then(),
                response.flatMapMany(ResponseEntity::getBody));
    }

    private Flux<JsonNode> invokeEventStream(ExecutionNode node, String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        ParameterizedTypeReference<JsonNode> returnType = new ParameterizedTypeReference<>() {};
        return apiClient.invokeAPI(
                        "/api/event", HttpMethod.GET, Map.of(), new LinkedMultiValueMap<>(), null,
                        new HttpHeaders(), new LinkedMultiValueMap<>(), new LinkedMultiValueMap<>(),
                        List.of(MediaType.TEXT_EVENT_STREAM), MediaType.APPLICATION_JSON, new String[]{}, returnType)
                .bodyToFlux(returnType);
    }

    /**
     * 查询 session Diff，并把 SnapshotFileDiff 映射为平台稳定 Diff 文件 DTO。
     */
    @Override
    public Mono<OpencodeDiffResult> getDiff(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String messageId,
            String traceId) {
        // V2 session.diff 只接受 from/to/context；平台 messageId 表示本轮 USER 锚点，映射为 from。
        Map<String, String> query = optionalText(messageId) == null ? Map.of() : Map.of("from", messageId);
        return invokeJson(node, "GET", "/api/session/" + opencodeSessionId + "/diff", Map.of(), null, query, traceId)
                .map(this::toDiffFiles)
                .map(OpencodeDiffResult::new);
    }

    /**
     * 拒绝指定 message/part 的 Diff，绕开 generated 参数类重名问题直接发送稳定 JSON。
     */
    @Override
    public Mono<OpencodeRejectDiffResult> rejectDiff(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String messageId,
            String partId,
            String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        // generated SessionApi 的参数包装类遮蔽了 model.SessionRevertRequest，这里直接构造稳定 JSON 请求体。
        // V2 的 revert/stage 以 messageID 和 files 控制范围；partID 只保留在平台审计上下文中。
        Map<String, Object> request = Map.of("messageID", messageId);
        ParameterizedTypeReference<Void> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("sessionID", opencodeSessionId);
        // V2 session 已绑定创建时的 location，固定上下文操作不再重复发送目录 query。
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        HttpHeaders headerParams = new HttpHeaders();
        MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
        MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();
        List<MediaType> accepts = apiClient.selectHeaderAccept(new String[]{"application/json"});
        MediaType contentType = apiClient.selectHeaderContentType(new String[]{"application/json"});
        return apiClient.invokeAPI(
                        "/api/session/" + opencodeSessionId + "/revert/stage",
                        HttpMethod.POST,
                        pathParams,
                        queryParams,
                        request,
                        headerParams,
                        cookieParams,
                        formParams,
                        accepts,
                        contentType,
                        new String[]{},
                        returnType)
                .bodyToMono(returnType)
                .thenReturn(new OpencodeRejectDiffResult(true));
    }

    /** 直接调用原生 unrevert；generated SDK 模型仍不向外暴露。 */
    @Override
    public Mono<OpencodeUnrevertResult> unrevert(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        ParameterizedTypeReference<Void> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("sessionID", opencodeSessionId);
        // V2 session 已绑定创建时的 location，固定上下文操作不再重复发送目录 query。
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        return apiClient.invokeAPI(
                        "/api/session/" + opencodeSessionId + "/revert",
                        HttpMethod.DELETE,
                        pathParams,
                        queryParams,
                        null,
                        new HttpHeaders(),
                        new LinkedMultiValueMap<>(),
                        new LinkedMultiValueMap<>(),
                        apiClient.selectHeaderAccept(new String[]{"application/json"}),
                        apiClient.selectHeaderContentType(new String[]{}),
                        new String[]{},
                        returnType)
                .bodyToMono(returnType)
                .thenReturn(new OpencodeUnrevertResult(true));
    }

    /**
     * 受控调用 opencode runtime API，追加 directory/workspace/query 后返回 JSON projection。
     */
    @Override
    public Mono<OpencodeRuntimeResult> runtime(
            ExecutionNode node,
            String method,
            String path,
            String directory,
            String workspace,
            Map<String, String> query,
            Object body,
            String traceId) {
        if ("GET".equals(method) && "/vcs/status".equals(path)) {
            // V2 将分支信息和文件状态拆成两个接口；平台的 status 仍同时提供两者。
            return Mono.zip(
                            runtime(node, method, "/api/vcs", directory, workspace, Map.of(), null, traceId),
                            runtime(node, method, "/api/vcs/status", directory, workspace, Map.of(), null, traceId))
                    .map(pair -> new OpencodeRuntimeResult(projectVcsStatus(pair.getT1().body(), pair.getT2().body())));
        }
        ApiClient apiClient = apiClient(node, traceId);
        ParameterizedTypeReference<JsonNode> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        String v2Path = OpencodeV2RouteMapper.map(path);
        Map<String, String> runtimeQuery = query == null ? Map.of() : new LinkedHashMap<>(query);
        if (v2Path.matches("/api/session/[^/]+/diff")) {
            // 兼容旧平台调用方仍传 messageID/messageId；V2 的 session.diff 语义是
            // “从该 USER 轮次开始”，因此统一归一化为 OpenAPI 的 from 查询参数。
            String messageId = runtimeQuery.remove("messageID");
            String camelCaseMessageId = runtimeQuery.remove("messageId");
            if (messageId == null) messageId = camelCaseMessageId;
            if (messageId != null && !messageId.isBlank() && !runtimeQuery.containsKey("from")) {
                runtimeQuery.put("from", messageId);
            }
        }
        boolean toolCatalog = "/experimental/tool".equals(path) || "/experimental/tool/ids".equals(path);
        if ("/file/content".equals(path) && runtimeQuery.containsKey("path")) {
            v2Path = "/api/fs/read/" + runtimeQuery.remove("path");
        }
        if (toolCatalog) {
            // V2 RPC 返回注册工具目录；当前协议没有 V1 的 provider/model 专项过滤。
            runtimeQuery.remove("provider");
            runtimeQuery.remove("model");
        }
        Matcher oauthCallback = PROVIDER_OAUTH_CALLBACK.matcher(path == null ? "" : path);
        if (oauthCallback.matches() && body instanceof Map<?, ?> rawBody) {
            String attemptId = firstText(rawBody, "attemptID", "attemptId", "id");
            if (attemptId != null) {
                v2Path = "/api/integration/" + oauthCallback.group(1)
                        + "/connect/oauth/" + attemptId + "/complete";
            }
        }
        if ("GET".equals(method) && "/file/content".equals(path)) {
            // V1 返回 FileContent JSON，而 V2 fs.read 直接返回文件字节；在 client 边界恢复
            // 原有平台 DTO，避免二进制响应被通用 JsonNode 解码器误判为坏网关。
            return invokeFileContent(apiClient, v2Path, directory, runtimeQuery);
        }
        Object v2Body = toolCatalog ? Map.of("input", Map.of())
                : noBodyEndpoint(v2Path, method) ? null : normalizeRuntimeBody(v2Path, body);
        HttpMethod v2Method = toolCatalog ? HttpMethod.POST : HttpMethod.valueOf(method);
        MultiValueMap<String, String> queryParams = queryParams(apiClient, v2Path, directory);
        runtimeQuery.forEach((name, value) -> {
            if (value != null && !value.isBlank()) {
                queryParams.putAll(apiClient.parameterToMultiValueMap(null, name, value));
            }
        });
        HttpHeaders headerParams = new HttpHeaders();
        MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
        MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();
        List<MediaType> accepts = apiClient.selectHeaderAccept(new String[]{"application/json"});
        MediaType contentType = apiClient.selectHeaderContentType(new String[]{"application/json"});
        return apiClient.invokeAPI(
                        v2Path,
                        v2Method,
                        pathParams,
                        queryParams,
                        v2Body,
                        headerParams,
                        cookieParams,
                        formParams,
                        accepts,
                        contentType,
                        new String[]{},
                        returnType)
                .bodyToMono(returnType)
                .defaultIfEmpty(objectMapper.createObjectNode().put("accepted", true))
                .map(result -> toolCatalog ? projectRuntimeToolCatalog(path, result)
                        : protectRuntimeCatalog(path, projectRuntimeResponse(path, result)))
                .map(OpencodeRuntimeResult::new);
    }

    private Mono<OpencodeRuntimeResult> invokeFileContent(
            ApiClient apiClient,
            String v2Path,
            String directory,
            Map<String, String> runtimeQuery) {
        MultiValueMap<String, String> queryParams = queryParams(apiClient, v2Path, directory);
        runtimeQuery.forEach((name, value) -> {
            if (value != null && !value.isBlank()) {
                queryParams.putAll(apiClient.parameterToMultiValueMap(null, name, value));
            }
        });
        ParameterizedTypeReference<byte[]> returnType = new ParameterizedTypeReference<>() {
        };
        List<MediaType> accepts = List.of(
                MediaType.APPLICATION_OCTET_STREAM,
                MediaType.TEXT_PLAIN,
                MediaType.APPLICATION_JSON);
        return apiClient.invokeAPI(
                        v2Path,
                        HttpMethod.GET,
                        Map.of(),
                        queryParams,
                        null,
                        new HttpHeaders(),
                        new LinkedMultiValueMap<>(),
                        new LinkedMultiValueMap<>(),
                        accepts,
                        apiClient.selectHeaderContentType(new String[]{}),
                        new String[]{},
                        returnType)
                .toEntity(byte[].class)
                .map(response -> new OpencodeRuntimeResult(projectFileContent(response)));
    }

    private JsonNode projectFileContent(ResponseEntity<byte[]> response) {
        byte[] bytes = response.getBody() == null ? new byte[0] : response.getBody();
        String text = decodeUtf8(bytes);
        if (text != null && !containsNul(bytes)) {
            return objectMapper.createObjectNode()
                    .put("type", "text")
                    .put("content", text.trim());
        }
        var projected = objectMapper.createObjectNode()
                .put("type", "binary")
                .put("content", Base64.getEncoder().encodeToString(bytes))
                .put("encoding", "base64");
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null) {
            projected.put("mimeType", contentType.getType() + "/" + contentType.getSubtype());
        }
        return projected;
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    private boolean containsNul(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) return true;
        }
        return false;
    }

    /** V2 的 location envelope 与平台旧运行态 DTO 在 client 边界归一化。 */
    private JsonNode projectRuntimeResponse(String path, JsonNode result) {
        if ("/mcp".equals(path) && result.path("data").isArray()) {
            var projected = objectMapper.createObjectNode();
            result.path("data").forEach(server -> {
                String name = server.path("name").asText("");
                if (name.isBlank()) return;
                var status = objectMapper.createObjectNode();
                status.put("name", name);
                status.put("status", server.path("status").path("status").asText("unknown"));
                if (server.path("status").path("error").isTextual()) {
                    status.set("error", server.path("status").path("error"));
                }
                projected.set(name, status);
            });
            return projected;
        }
        if ("/experimental/resource".equals(path) && result.path("data").isObject()) {
            var resources = objectMapper.createArrayNode();
            JsonNode catalog = result.path("data");
            if (catalog.path("resources").isArray()) {
                catalog.path("resources").forEach(resources::add);
            }
            if (catalog.path("templates").isArray()) {
                catalog.path("templates").forEach(template -> {
                    var projected = template.deepCopy();
                    if (projected instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                        object.put("type", "template");
                        object.set("uri", template.path("uriTemplate"));
                    }
                    resources.add(projected);
                });
            }
            return resources;
        }
        if ("/lsp".equals(path) && result.isArray()) {
            // /api/config 是配置清单，不含 LSP 进程实时健康；不能把任意配置误报为 ready。
            var projected = objectMapper.createObjectNode();
            projected.put("status", "unknown");
            result.forEach(entry -> {
                if (entry.path("type").asText().equals("document")
                        && entry.path("info").path("lsp").isBoolean()
                        && !entry.path("info").path("lsp").asBoolean()) {
                    projected.put("status", "disabled");
                }
            });
            return projected;
        }
        return result;
    }

    /**
     * V2 模型、供应商和配置源会原样携带运行凭据。目录只用于客户端选择与展示，
     * 因此在唯一 OpenCode 响应边界删除凭据字段及可容纳任意密钥的配置容器。
     * 保留 location/data envelope、模型能力和 provider 策略，避免前端协议变化。
     */
    private JsonNode protectRuntimeCatalog(String path, JsonNode result) {
        if (path == null || !CATALOG_RESPONSE_PATHS.contains(path)
                && !path.startsWith("/provider/") && !path.startsWith("/auth/")) {
            return result;
        }
        return stripPrivateCatalogFields(result);
    }

    private JsonNode stripPrivateCatalogFields(JsonNode value) {
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(stripPrivateCatalogFields(item)));
            return result;
        }
        if (!value.isObject()) return value;
        ObjectNode result = objectMapper.createObjectNode();
        value.fields().forEachRemaining(field -> {
            String normalized = field.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!PRIVATE_CATALOG_FIELDS.contains(normalized)
                    && !normalized.endsWith("apikey") && !normalized.endsWith("token")
                    && !normalized.endsWith("secret") && !normalized.endsWith("password")
                    && !normalized.endsWith("credential")) {
                result.set(field.getKey(), stripPrivateCatalogFields(field.getValue()));
            }
        });
        return result;
    }

    private JsonNode projectVcsStatus(JsonNode info, JsonNode fileStatus) {
        var projected = objectMapper.createObjectNode();
        projected.put("status", "ready");
        JsonNode branch = info.path("data").path("branch");
        if (branch.path("current").isTextual()) projected.set("branch", branch.path("current"));
        if (branch.path("default").isTextual()) projected.set("defaultBranch", branch.path("default"));
        projected.set("files", fileStatus.path("data").isArray()
                ? fileStatus.path("data") : objectMapper.createArrayNode());
        return projected;
    }

    /** V2 插件 RPC 输出恢复旧平台 tool / tool-id 数组，不把 RPC envelope 暴露给前端。 */
    private JsonNode projectRuntimeToolCatalog(String path, JsonNode result) {
        JsonNode tools = result.path("output");
        if (!tools.isArray()) throw new IllegalStateException("OpenCode V2 tool catalog RPC returned no output array");
        if (!"/experimental/tool/ids".equals(path)) return tools;
        var ids = objectMapper.createArrayNode();
        tools.forEach(tool -> {
            JsonNode id = tool.path("toolId");
            if (id.isTextual()) ids.add(id.asText());
        });
        return ids;
    }

    private Object normalizeRuntimeBody(String v2Path, Object body) {
        if (!(body instanceof Map<?, ?> raw)) {
            return body;
        }
        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key instanceof String name && value != null) source.put(name, value);
        });
        if (v2Path.matches("/api/integration/[^/]+/connect/oauth/[^/]+/complete")) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            copyIfPresent(source, normalized, "code");
            return normalized;
        }
        if (v2Path.endsWith("/command")) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            String name = firstText(source, "name", "command");
            String text = firstText(source, "text", "arguments");
            if (name != null) normalized.put("name", name);
            normalized.put("text", text == null ? "" : text);
            copyIfPresent(source, normalized, "files", "agents", "skills", "delivery");
            return normalized;
        }
        if (v2Path.endsWith("/compact")) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            copyIfPresent(source, normalized, "id", "delivery");
            return normalized;
        }
        if (v2Path.endsWith("/fork")) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            String before = firstText(source, "before", "messageID", "messageId");
            if (before != null) normalized.put("before", before);
            return normalized;
        }
        if (v2Path.endsWith("/revert/stage")) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            String messageId = firstText(source, "messageID", "messageId", "id");
            if (messageId != null) normalized.put("messageID", messageId);
            copyIfPresent(source, normalized, "files");
            return normalized;
        }
        if (v2Path.matches(".*/permission/[^/]+/reply$")) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            String decision = firstText(source, "decision", "reply");
            if (decision != null) normalized.put("decision", decision);
            copyIfPresent(source, normalized, "message");
            return normalized;
        }
        if (v2Path.matches(".*/form/[^/]+/reply$")) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            Object answer = source.get("answer");
            if (answer instanceof Map<?, ?>) {
                normalized.put("answer", answer);
            } else if (source.get("answers") instanceof List<?> answers) {
                // V1 answers are ordered arrays; V2 forms are keyed by field key. Numeric
                // keys remain a deterministic compatibility projection when the caller has
                // not supplied the V2 field-key map.
                LinkedHashMap<String, Object> keyed = new LinkedHashMap<>();
                for (int i = 0; i < answers.size(); i++) keyed.put(Integer.toString(i), answers.get(i));
                normalized.put("answer", keyed);
            }
            return normalized;
        }
        if (v2Path.endsWith("/shell")) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            String command = firstText(source, "command", "text");
            if (command != null) normalized.put("command", command);
            copyIfPresent(source, normalized, "id");
            return normalized;
        }
        return body;
    }

    /** V2 的取消、拒绝和重载路由无请求体，旧平台空对象不得进入严格 schema。 */
    private boolean noBodyEndpoint(String path, String method) {
        if ("POST".equals(method)) {
            return path.equals("/api/location/reload")
                    || path.matches("/api/session/[^/]+/interrupt")
                    || path.matches("/api/experimental/mcp/[^/]+/(connect|disconnect)");
        }
        if ("DELETE".equals(method)) {
            return path.matches("/api/session/[^/]+")
                    || path.matches("/api/session/[^/]+/revert")
                    || path.matches("/api/session/[^/]+/form/[^/]+");
        }
        return false;
    }

    private static String firstText(Map<?, ?> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String text && !text.isBlank()) return text;
        }
        return null;
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) if (source.containsKey(key)) target.put(key, source.get(key));
    }

    /**
     * 读取 opencode projected messages，并转换为平台 session message 投影。
     */
    @Override
    public Mono<OpencodeSessionMessagesResult> sessionMessages(
            ExecutionNode node,
            String opencodeSessionId,
            int limit,
            String order,
            String cursor,
            String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        ParameterizedTypeReference<JsonNode> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("sessionID", opencodeSessionId);
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "order", order));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "cursor", optionalText(cursor)));
        HttpHeaders headerParams = new HttpHeaders();
        MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
        MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();
        List<MediaType> accepts = apiClient.selectHeaderAccept(new String[]{"application/json"});
        MediaType contentType = apiClient.selectHeaderContentType(new String[]{});
        // generated Message/Part union 会把 user 误收窄成 assistant，使用同一 generated ApiClient 读取稳定原始 JSON。
        return apiClient.invokeAPI(
                        "/api/session/" + opencodeSessionId + "/message",
                        HttpMethod.GET,
                        pathParams,
                        queryParams,
                        null,
                        headerParams,
                        cookieParams,
                        formParams,
                        accepts,
                        contentType,
                        new String[]{},
                        returnType)
                .toEntity(returnType)
                .map(response -> toSessionMessagesResult(response, order, opencodeSessionId));
    }

    /**
     * 为每次 gateway 调用创建带 baseUrl 和 traceId header 的 generated ApiClient。
     */
    private ApiClient apiClient(ExecutionNode node, String traceId) {
        ApiClient client = new ApiClient(webClient(node, traceId))
                .setBasePath(node.baseUrl())
                .addDefaultHeader(TraceConstants.TRACE_ID_HEADER, traceId);
        String password = System.getProperty(
                "test.agent.opencode.server.password",
                System.getenv("TEST_AGENT_OPENCODE_SERVER_PASSWORD"));
        if (password != null && !password.isBlank()) {
            String credentials = Base64.getEncoder().encodeToString(("opencode:" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            client.addDefaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials);
        }
        return client;
    }

    /** V2 协议统一使用原始 JSON，避免 generated union DTO 将业务语义收窄。 */
    private Mono<JsonNode> invokeJson(
            ExecutionNode node,
            String method,
            String path,
            Map<String, Object> pathParams,
            Object body,
            Map<String, String> query,
            String traceId) {
        ApiClient client = apiClient(node, traceId);
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        query.forEach((name, value) -> {
            if (value != null && !value.isBlank()) queryParams.putAll(client.parameterToMultiValueMap(null, name, value));
        });
        return client.invokeAPI(
                        path,
                        HttpMethod.valueOf(method),
                        pathParams == null ? Map.of() : pathParams,
                        queryParams,
                        body,
                        new HttpHeaders(),
                        new LinkedMultiValueMap<>(),
                        new LinkedMultiValueMap<>(),
                        client.selectHeaderAccept(new String[]{"application/json"}),
                        client.selectHeaderContentType(body == null ? new String[]{} : new String[]{"application/json"}),
                        new String[]{},
                        new ParameterizedTypeReference<JsonNode>() {})
                .bodyToMono(new ParameterizedTypeReference<JsonNode>() {})
                .defaultIfEmpty(objectMapper.createObjectNode());
    }

    /**
     * opencode session message 快照会包含完整 tool/read/write parts；默认 256KB 缓冲会导致历史恢复失败。
     */
    private WebClient webClient(ExecutionNode node, String traceId) {
        Objects.requireNonNull(node, "node must not be null");
        for (OpencodeWebClientTransport transport : transports) {
            if (transport.supports(node)) {
                return transport.create(node, traceId, OPENCODE_RESPONSE_MAX_IN_MEMORY_SIZE);
            }
        }
        return ApiClient.buildWebClientBuilder(ApiClient.createDefaultMapper(null))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(OPENCODE_RESPONSE_MAX_IN_MEMORY_SIZE))
                .build();
    }

    /**
     * 组装 opencode 约定的 directory/workspace query，workspace 为空时不传。
     */
    private MultiValueMap<String, String> queryParams(ApiClient apiClient, String v2Path, String directory) {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        // V2 的 location-scoped endpoint 使用 deepObject；只有 session 列表保留
        // OpenCode 自身定义的 directory 参数，避免把 workspace/directory 透传到严格 schema。
        if ("/api/session".equals(v2Path) && optionalText(directory) != null) {
            queryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        } else if (supportsLocationQuery(v2Path) && optionalText(directory) != null) {
            queryParams.putAll(apiClient.parameterToMultiValueMap(null, "location[directory]", directory));
        }
        return queryParams;
    }

    private boolean supportsLocationQuery(String path) {
        return path.equals("/api/location")
                || path.equals("/api/agent")
                || path.startsWith("/api/agent/")
                || path.equals("/api/plugin")
                || path.startsWith("/api/plugin/")
                || path.startsWith("/api/rpc/")
                || path.equals("/api/model")
                || path.equals("/api/model/default")
                || path.equals("/api/provider")
                || path.startsWith("/api/provider/")
                || path.equals("/api/integration")
                || path.startsWith("/api/integration/")
                || path.equals("/api/mcp")
                || path.startsWith("/api/experimental/mcp/")
                || path.equals("/api/mcp/resource")
                || path.equals("/api/form")
                || path.equals("/api/permission/request")
                || path.startsWith("/api/fs/read/")
                || path.equals("/api/fs/list")
                || path.equals("/api/fs/find")
                || path.equals("/api/experimental/fs/write")
                || path.equals("/api/command")
                || path.equals("/api/skill")
                || path.startsWith("/api/pty")
                || path.startsWith("/api/shell")
                || path.equals("/api/reference")
                || path.equals("/api/vcs")
                || path.equals("/api/vcs/base")
                || path.equals("/api/vcs/status")
                || path.equals("/api/vcs/branch")
                || path.equals("/api/vcs/diff")
                || path.equals("/api/config");
    }

    /**
     * 从 create session JSON 响应中提取远端 session id，缺失时让 facade 转换为平台错误。
     */
    private String extractSessionId(JsonNode body) {
        JsonNode candidate = body == null ? null : body.path("id");
        if (candidate == null || !candidate.isTextual() || candidate.asText().isBlank()) {
            // V2 的创建接口返回 {"data":{"id":"ses_..."}}；兼容同版本
            // 部分部署仍返回顶层 id，避免把合法 session 误报为协议错误。
            candidate = body == null ? null : body.path("data").path("id");
        }
        if (candidate == null || !candidate.isTextual() || candidate.asText().isBlank()) {
            throw new IllegalStateException("opencode create session response missing id");
        }
        return candidate.asText();
    }

    /**
     * 将 generated Diff 文件转换为平台 DTO，并为缺省状态提供 modified 默认值。
     */
    private List<OpencodeDiffFile> toDiffFiles(JsonNode body) {
        JsonNode data = body != null && body.has("data") ? body.get("data") : body;
        if (data == null || !data.isArray()) return List.of();
        List<OpencodeDiffFile> files = new ArrayList<>();
        data.forEach(item -> files.add(new OpencodeDiffFile(
                item.path("file").asText("unknown"),
                item.path("patch").asText(""),
                item.path("additions").asLong(0),
                item.path("deletions").asLong(0),
                item.path("status").asText("modified"))));
        return List.copyOf(files);
    }

    /**
     * 将标准 session message envelope 转换为平台结果，并按调用方要求调整顺序。
     */
    private OpencodeSessionMessagesResult toSessionMessagesResult(
            ResponseEntity<JsonNode> response,
            String order,
            String sessionId) {
        List<OpencodeSessionMessage> messages = new ArrayList<>();
        JsonNode payload = response.getBody();
        JsonNode data = payload != null && payload.isArray() ? payload : payload == null ? null : payload.path("data");
        if (data != null && data.isArray()) {
            data.forEach(envelope -> {
                JsonNode infoNode = envelope != null && envelope.has("info") ? envelope.path("info") : envelope;
                Map<String, Object> info = objectMap(infoNode);
                String v2Type = stringValue(info.get("type"));
                if (v2Type != null && !"user".equals(v2Type) && !"assistant".equals(v2Type)) {
                    return;
                }
                Object rawParts = objectValue(envelope, "parts");
                messages.add(toSessionMessage(info, rawParts, sessionId));
            });
        }
        if ("desc".equalsIgnoreCase(order)) {
            Collections.reverse(messages);
        }
        return new OpencodeSessionMessagesResult(
                List.copyOf(messages),
                payload != null && payload.isObject() && payload.has("cursor")
                        ? textValue(payload.path("cursor").path("previous")) : null,
                payload != null && payload.isObject() && payload.has("cursor")
                        ? textValue(payload.path("cursor").path("next"))
                        : response.getHeaders().getFirst("X-Next-Cursor"));
    }

    /**
     * 规范化单条 session message，补充 messageID/messageId 和 role 兼容字段。
     */
    private OpencodeSessionMessage toSessionMessage(Map<String, Object> raw, Object rawParts, String sessionId) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(raw);
        String messageId = stringValue(raw.get("id"));
        String v2Type = stringValue(raw.get("type"));
        if ("user".equals(v2Type) || "assistant".equals(v2Type)) {
            normalized.put("role", v2Type);
        }
        normalized.putIfAbsent("sessionID", sessionId);
        if (messageId != null) {
            normalized.putIfAbsent("messageID", messageId);
            normalized.putIfAbsent("messageId", messageId);
        }
        Object projectedParts = rawParts;
        if (projectedParts == null && "assistant".equals(v2Type)) {
            projectedParts = raw.get("content");
        }
        if (projectedParts == null && "user".equals(v2Type)) {
            List<Map<String, Object>> userParts = new ArrayList<>();
            String text = stringValue(raw.get("text"));
            if (text != null) userParts.add(Map.of("type", "text", "text", text));
            if (raw.get("files") instanceof List<?> files) {
                for (Object file : files) {
                    if (file instanceof Map<?, ?> map) {
                        LinkedHashMap<String, Object> part = new LinkedHashMap<>(stringObjectMap(map));
                        part.put("type", "file");
                        userParts.add(part);
                    }
                }
            }
            projectedParts = userParts;
        }
        return new OpencodeSessionMessage(immutableWithoutNulls(normalized),
                partsFromList(projectedParts, sessionId, messageId));
    }

    /**
     * 从 opencode message envelope 中抽取 part 列表，非列表内容按空列表处理。
     */
    private List<Map<String, Object>> partsFromList(Object rawParts, String sessionId, String messageId) {
        if (!(rawParts instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> identified = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            Object item = list.get(index);
            if (item instanceof Map<?, ?> raw && !OpencodePromptPart.isInternalRunContextPart(raw)) {
                identified.add(OpencodeV2ContentAdapter.project(raw, sessionId, messageId, index));
            }
        }
        return identified;
    }

    /**
     * 把原始 JSON 对象安全收敛为字符串键 Map。
     */
    private Map<String, Object> stringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key instanceof String name && item != null) {
                result.put(name, item);
            }
        });
        return Map.copyOf(result);
    }

    private Map<String, Object> objectMap(JsonNode value) {
        if (value == null || !value.isObject()) return Map.of();
        return stringObjectMap(objectMapper.convertValue(value, Map.class));
    }

    private Object objectValue(JsonNode value, String field) {
        if (value == null || !value.isObject() || !value.has(field)) return null;
        return objectMapper.convertValue(value.get(field), Object.class);
    }

    private String textValue(JsonNode value) {
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    /**
     * 读取非空字符串字段，空白字符串按缺失处理。
     */
    private String stringValue(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    /**
     * 复制 Map 并过滤 null 键值，防止 generated DTO 的空字段泄露给上游。
     */
    private Map<String, Object> immutableWithoutNulls(Map<String, Object> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(key, value);
            }
        });
        return Map.copyOf(result);
    }

    /**
     * 规范化可选文本参数，避免向 opencode 发送空白 query 或空白 body 字段。
     */
    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
