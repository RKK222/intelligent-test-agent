package com.icbc.testagent.opencode.client;

import com.example.opencode.sdk.ApiClient;
import com.example.opencode.sdk.api.EventApi;
import com.example.opencode.sdk.api.GlobalApi;
import com.example.opencode.sdk.api.SessionApi;
import com.example.opencode.sdk.api.SessionsApi;
import com.example.opencode.sdk.model.SnapshotFileDiff;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.observability.TraceConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用 opencode health API，并把 generated 响应归一为平台健康结果。
     */
    @Override
    public Mono<OpencodeHealthResult> health(ExecutionNode node, String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        return new GlobalApi(apiClient)
                .globalHealth()
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
        ApiClient apiClient = apiClient(node, traceId);
        ParameterizedTypeReference<JsonNode> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        MultiValueMap<String, String> queryParams = queryParams(apiClient, directory, workspace);
        Map<String, Object> request = title == null ? Map.of() : Map.of("title", title);
        HttpHeaders headerParams = new HttpHeaders();
        MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
        MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();
        List<MediaType> accepts = apiClient.selectHeaderAccept(new String[]{"application/json"});
        MediaType contentType = apiClient.selectHeaderContentType(new String[]{"application/json"});
        return apiClient.invokeAPI(
                        "/session",
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
                .map(body -> new OpencodeCreateSessionResult(extractSessionId(body)));
    }

    /**
     * 通过 v2 session get 判断远端 session 是否存在；404 由 facade 统一转换为 false。
     */
    @Override
    public Mono<Boolean> sessionExists(ExecutionNode node, String opencodeSessionId, String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        return new SessionsApi(apiClient)
                .v2SessionGet(opencodeSessionId)
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
        ApiClient apiClient = apiClient(node, traceId);
        return new SessionApi(apiClient)
                .sessionAbort(opencodeSessionId, directory, optionalText(workspace))
                .map(cancelled -> new OpencodeCancelResult(Boolean.TRUE.equals(cancelled)));
    }

    /**
     * 通过 prompt_async 启动 opencode 运行，使用稳定 JSON Map 隔离 generated union DTO 差异。
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
        ApiClient apiClient = apiClient(node, traceId);
        // prompt_async 的 parts 是 union schema；用稳定 JSON Map 避免 generated DTO 对 file/source 字段的类型遮蔽。
        Map<String, Object> request = promptAsyncRequest(
                parts, messageId, agent, system, modelProviderId, modelId, variant, tools, prompt);
        logPromptAsyncRequestPrepared(
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
        ParameterizedTypeReference<Void> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("sessionID", opencodeSessionId);
        MultiValueMap<String, String> queryParams = queryParams(apiClient, directory, workspace);
        HttpHeaders headerParams = new HttpHeaders();
        MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
        MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();
        List<MediaType> accepts = apiClient.selectHeaderAccept(new String[]{"application/json"});
        MediaType contentType = apiClient.selectHeaderContentType(new String[]{"application/json"});
        return apiClient.invokeAPI(
                        "/session/{sessionID}/prompt_async",
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
                .doOnSuccess(ignored -> logPromptAsyncRequestAccepted(
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
        ApiClient apiClient = apiClient(node, traceId);
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("command", command);
        request.put("arguments", arguments == null ? "" : arguments);
        String optionalMessageId = optionalText(messageId);
        if (optionalMessageId != null) {
            request.put("messageID", optionalMessageId);
        }
        String optionalAgent = optionalText(agent);
        if (optionalAgent != null) {
            request.put("agent", optionalAgent);
        }
        String optionalModelProvider = optionalText(modelProviderId);
        String optionalModelId = optionalText(modelId);
        if (optionalModelProvider != null && optionalModelId != null) {
            request.put("model", optionalModelProvider + "/" + optionalModelId);
        }
        String optionalVariant = optionalText(variant);
        if (optionalVariant != null) {
            request.put("variant", optionalVariant);
        }
        List<Map<String, Object>> fileParts = parts == null
                ? List.of()
                : parts.stream()
                        .filter(part -> "file".equals(part.type()))
                        .map(OpencodePromptPart::toRequestBody)
                        .toList();
        if (!fileParts.isEmpty()) {
            request.put("parts", fileParts);
        }
        ParameterizedTypeReference<JsonNode> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("sessionID", opencodeSessionId);
        MultiValueMap<String, String> queryParams = queryParams(apiClient, directory, workspace);
        HttpHeaders headerParams = new HttpHeaders();
        MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
        MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();
        List<MediaType> accepts = apiClient.selectHeaderAccept(new String[]{"application/json"});
        MediaType contentType = apiClient.selectHeaderContentType(new String[]{"application/json"});
        return apiClient.invokeAPI(
                        "/session/{sessionID}/command",
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
                        returnType)
                .bodyToMono(returnType)
                .thenReturn(new OpencodeStartRunResult(true));
    }

    /**
     * 构造 prompt_async 请求体，只写入调用方显式传入的可选运行态选择字段。
     */
    private Map<String, Object> promptAsyncRequest(
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
        if (optionalMessageId != null) {
            request.put("messageID", optionalMessageId);
        }
        String optionalAgent = optionalText(agent);
        if (optionalAgent != null) {
            request.put("agent", optionalAgent);
        }
        String optionalSystem = optionalText(system);
        if (optionalSystem != null) {
            request.put("system", optionalSystem);
        }
        String optionalModelProvider = optionalText(modelProviderId);
        String optionalModelId = optionalText(modelId);
        if (optionalModelProvider != null && optionalModelId != null) {
            request.put("model", Map.of("providerID", optionalModelProvider, "modelID", optionalModelId));
        }
        String optionalVariant = optionalText(variant);
        if (optionalVariant != null) {
            request.put("variant", optionalVariant);
        }
        if (tools != null && !tools.isEmpty()) {
            request.put("tools", Map.copyOf(tools));
        }
        List<Map<String, Object>> requestParts = (parts == null || parts.isEmpty()
                ? List.of(OpencodePromptPart.text(prompt))
                : parts).stream()
                .map(OpencodePromptPart::toRequestBody)
                .toList();
        request.put("parts", requestParts);
        return Map.copyOf(request);
    }

    /**
     * 记录发给 opencode prompt_async 的结构摘要，避免正文、data URL 和 source.text 原文进入日志。
     */
    private void logPromptAsyncRequestPrepared(
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
                "opencode_prompt_async_request_prepared traceId={} nodeId={} baseUrl={} sessionId={} directoryPresent={} workspacePresent={} messageId={} agent={} modelProviderId={} modelId={} variant={} partsCount={} partsSummary={}",
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
                promptAsyncParts(request).size(),
                summarizePromptAsyncRequest(request));
    }

    /**
     * 记录 opencode 已接受 prompt_async 请求，便于和后续 global/event 中的 user message parts 对照。
     */
    private void logPromptAsyncRequestAccepted(
            ExecutionNode node,
            String opencodeSessionId,
            String messageId,
            String traceId,
            Map<String, Object> request) {
        LOGGER.info(
                "opencode_prompt_async_request_accepted traceId={} nodeId={} baseUrl={} sessionId={} messageId={} partsCount={} partsSummary={}",
                traceId,
                node.executionNodeId().value(),
                node.baseUrl(),
                opencodeSessionId,
                optionalText(messageId),
                promptAsyncParts(request).size(),
                summarizePromptAsyncRequest(request));
    }

    /**
     * 提取 prompt_async parts 的脱敏摘要，保留 type/mime/filename/source 范围用于核对原生附件形态。
     */
    static List<Map<String, Object>> summarizePromptAsyncRequest(Map<String, Object> request) {
        return promptAsyncParts(request).stream()
                .map(GeneratedOpencodeSdkGateway::summarizePromptPart)
                .toList();
    }

    private static List<Map<String, Object>> promptAsyncParts(Map<String, Object> request) {
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
        ApiClient apiClient = apiClient(node, traceId);
        return new EventApi(apiClient)
                .eventSubscribeWithResponseSpec(directory, optionalText(workspace))
                .bodyToFlux(JsonNode.class);
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
        Mono<ResponseEntity<Flux<JsonNode>>> response = new EventApi(apiClient)
                .eventSubscribeWithResponseSpec(directory, optionalText(workspace))
                .toEntityFlux(JsonNode.class)
                .cache();
        return new OpencodeEventStream(
                response.then(),
                response.flatMapMany(ResponseEntity::getBody));
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
        ApiClient apiClient = apiClient(node, traceId);
        return new SessionApi(apiClient)
                .sessionDiff(opencodeSessionId, directory, optionalText(workspace), optionalText(messageId))
                .map(this::toDiffFile)
                .collectList()
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
        Map<String, Object> request = optionalText(partId) == null
                ? Map.of("messageID", messageId)
                : Map.of("messageID", messageId, "partID", optionalText(partId));
        ParameterizedTypeReference<Void> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("sessionID", opencodeSessionId);
        MultiValueMap<String, String> queryParams = queryParams(apiClient, directory, workspace);
        HttpHeaders headerParams = new HttpHeaders();
        MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
        MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();
        List<MediaType> accepts = apiClient.selectHeaderAccept(new String[]{"application/json"});
        MediaType contentType = apiClient.selectHeaderContentType(new String[]{"application/json"});
        return apiClient.invokeAPI(
                        "/session/{sessionID}/revert",
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
        ApiClient apiClient = apiClient(node, traceId);
        ParameterizedTypeReference<JsonNode> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        MultiValueMap<String, String> queryParams = queryParams(apiClient, directory, workspace);
        query.forEach((name, value) -> {
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
                        path,
                        HttpMethod.valueOf(method),
                        pathParams,
                        queryParams,
                        body,
                        headerParams,
                        cookieParams,
                        formParams,
                        accepts,
                        contentType,
                        new String[]{},
                        returnType)
                .bodyToMono(returnType)
                .defaultIfEmpty(objectMapper.createObjectNode().put("accepted", true))
                .map(OpencodeRuntimeResult::new);
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
        ParameterizedTypeReference<Map<String, Object>> returnType = new ParameterizedTypeReference<>() {
        };
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("sessionID", opencodeSessionId);
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "before", optionalText(cursor)));
        HttpHeaders headerParams = new HttpHeaders();
        MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
        MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();
        List<MediaType> accepts = apiClient.selectHeaderAccept(new String[]{"application/json"});
        MediaType contentType = apiClient.selectHeaderContentType(new String[]{});
        // generated Message/Part union 会把 user 误收窄成 assistant，使用同一 generated ApiClient 读取稳定原始 JSON。
        return apiClient.invokeAPI(
                        "/session/{sessionID}/message",
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
                .toEntityList(returnType)
                .map(response -> toSessionMessagesResult(response, order));
    }

    /**
     * 为每次 gateway 调用创建带 baseUrl 和 traceId header 的 generated ApiClient。
     */
    private ApiClient apiClient(ExecutionNode node, String traceId) {
        return new ApiClient(webClient())
                .setBasePath(node.baseUrl())
                .addDefaultHeader(TraceConstants.TRACE_ID_HEADER, traceId);
    }

    /**
     * opencode session message 快照会包含完整 tool/read/write parts；默认 256KB 缓冲会导致历史恢复失败。
     */
    private WebClient webClient() {
        return ApiClient.buildWebClientBuilder(ApiClient.createDefaultMapper(null))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(OPENCODE_RESPONSE_MAX_IN_MEMORY_SIZE))
                .build();
    }

    /**
     * 组装 opencode 约定的 directory/workspace query，workspace 为空时不传。
     */
    private MultiValueMap<String, String> queryParams(ApiClient apiClient, String directory, String workspace) {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        String optionalWorkspace = optionalText(workspace);
        if (optionalWorkspace != null) {
            queryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", optionalWorkspace));
        }
        return queryParams;
    }

    /**
     * 从 create session JSON 响应中提取远端 session id，缺失时让 facade 转换为平台错误。
     */
    private String extractSessionId(JsonNode body) {
        if (body == null || body.path("id").asText().isBlank()) {
            throw new IllegalStateException("opencode create session response missing id");
        }
        return body.path("id").asText();
    }

    /**
     * 将 generated Diff 文件转换为平台 DTO，并为缺省状态提供 modified 默认值。
     */
    private OpencodeDiffFile toDiffFile(SnapshotFileDiff diff) {
        String status = diff.getStatus() == null ? "modified" : diff.getStatus().getValue();
        return new OpencodeDiffFile(
                diff.getFile(),
                diff.getPatch(),
                toLong(diff.getAdditions()),
                toLong(diff.getDeletions()),
                status);
    }

    /**
     * 将标准 session message envelope 转换为平台结果，并按调用方要求调整顺序。
     */
    private OpencodeSessionMessagesResult toSessionMessagesResult(
            ResponseEntity<List<Map<String, Object>>> response,
            String order) {
        List<OpencodeSessionMessage> messages = new ArrayList<>();
        for (Map<String, Object> envelope : response.getBody() == null ? List.<Map<String, Object>>of() : response.getBody()) {
            Map<String, Object> info = stringObjectMap(envelope.get("info"));
            if (info.isEmpty()) {
                continue;
            }
            messages.add(toSessionMessage(info, envelope.get("parts")));
        }
        if ("desc".equalsIgnoreCase(order)) {
            Collections.reverse(messages);
        }
        return new OpencodeSessionMessagesResult(
                List.copyOf(messages),
                null,
                optionalText(response.getHeaders().getFirst("X-Next-Cursor")));
    }

    /**
     * 规范化单条 session message，补充 messageID/messageId 和 role 兼容字段。
     */
    private OpencodeSessionMessage toSessionMessage(Map<String, Object> raw, Object rawParts) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(raw);
        String messageId = stringValue(raw.get("id"));
        if (messageId != null) {
            normalized.putIfAbsent("messageID", messageId);
            normalized.putIfAbsent("messageId", messageId);
        }
        return new OpencodeSessionMessage(
                immutableWithoutNulls(normalized),
                partsFromList(rawParts, messageId));
    }

    /**
     * 从 opencode message envelope 中抽取 part 列表，非列表内容按空列表处理。
     */
    private List<Map<String, Object>> partsFromList(Object rawParts, String messageId) {
        if (!(rawParts instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> normalizePart((Map<?, ?>) item, messageId))
                .toList();
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

    /**
     * 规范化 message part，补齐大小写兼容 ID 字段和工具名 alias。
     */
    private Map<String, Object> normalizePart(Map<?, ?> rawPart, String messageId) {
        LinkedHashMap<String, Object> part = new LinkedHashMap<>();
        rawPart.forEach((key, value) -> {
            if (key instanceof String name) {
                part.put(name, value);
            }
        });
        if (messageId != null) {
            part.putIfAbsent("messageID", messageId);
            part.putIfAbsent("messageId", messageId);
        }
        String partId = stringValue(part.get("id"));
        if (partId != null) {
            part.putIfAbsent("partID", partId);
            part.putIfAbsent("partId", partId);
        }
        Object name = part.get("name");
        if (name instanceof String toolName && "tool".equals(part.get("type"))) {
            part.putIfAbsent("tool", toolName);
            part.putIfAbsent("toolName", toolName);
        }
        return immutableWithoutNulls(part);
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
     * 将 generated BigDecimal 计数字段转为 long；缺失计数按 0 处理。
     */
    private long toLong(BigDecimal value) {
        return value == null ? 0 : value.longValue();
    }

    /**
     * 规范化可选文本参数，避免向 opencode 发送空白 query 或空白 body 字段。
     */
    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
