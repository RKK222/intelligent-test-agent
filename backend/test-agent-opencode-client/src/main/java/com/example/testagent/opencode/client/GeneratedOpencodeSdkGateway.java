package com.example.testagent.opencode.client;

import com.example.opencode.sdk.ApiClient;
import com.example.opencode.sdk.api.EventApi;
import com.example.opencode.sdk.api.GlobalApi;
import com.example.opencode.sdk.api.MessagesApi;
import com.example.opencode.sdk.api.SessionApi;
import com.example.opencode.sdk.model.SnapshotFileDiff;
import com.example.opencode.sdk.model.SessionMessage;
import com.example.opencode.sdk.model.SessionMessagesResponse;
import com.example.opencode.sdk.model.SessionsResponseCursor;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.observability.TraceConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * generated SDK 的唯一直接调用点；本类之外不应 import com.example.opencode.sdk.*。
 */
@Component
public class GeneratedOpencodeSdkGateway implements OpencodeSdkGateway {

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
        Map<String, Object> request = Map.of("title", title);
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
            String modelProviderId,
            String modelId,
            String variant,
            String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        // prompt_async 的 parts 是 union schema；用稳定 JSON Map 避免 generated DTO 对 file/source 字段的类型遮蔽。
        Map<String, Object> request = promptAsyncRequest(parts, messageId, agent, modelProviderId, modelId, variant, prompt);
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
                .thenReturn(new OpencodeStartRunResult(true));
    }

    /**
     * 构造 prompt_async 请求体，只写入调用方显式传入的可选运行态选择字段。
     */
    private Map<String, Object> promptAsyncRequest(
            List<OpencodePromptPart> parts,
            String messageId,
            String agent,
            String modelProviderId,
            String modelId,
            String variant,
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
        String optionalModelProvider = optionalText(modelProviderId);
        String optionalModelId = optionalText(modelId);
        if (optionalModelProvider != null && optionalModelId != null) {
            request.put("model", Map.of("providerID", optionalModelProvider, "modelID", optionalModelId));
        }
        String optionalVariant = optionalText(variant);
        if (optionalVariant != null) {
            request.put("variant", optionalVariant);
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
        return new MessagesApi(apiClient)
                .v2SessionMessages(
                        opencodeSessionId,
                        BigDecimal.valueOf(limit),
                        optionalText(order),
                        optionalText(cursor))
                .map(this::toSessionMessagesResult);
    }

    /**
     * 为每次 gateway 调用创建带 baseUrl 和 traceId header 的 generated ApiClient。
     */
    private ApiClient apiClient(ExecutionNode node, String traceId) {
        return new ApiClient()
                .setBasePath(node.baseUrl())
                .addDefaultHeader(TraceConstants.TRACE_ID_HEADER, traceId);
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
     * 将 generated messages 响应转换为平台结果，并保留分页 cursor 的不透明字符串。
     */
    private OpencodeSessionMessagesResult toSessionMessagesResult(SessionMessagesResponse response) {
        List<OpencodeSessionMessage> messages = response == null || response.getData() == null
                ? List.of()
                : response.getData().stream()
                        .map(this::toSessionMessage)
                        .toList();
        SessionsResponseCursor cursor = response == null ? null : response.getCursor();
        return new OpencodeSessionMessagesResult(
                messages,
                cursor == null ? null : cursor.getPrevious(),
                cursor == null ? null : cursor.getNext());
    }

    /**
     * 规范化单条 session message，补充 messageID/messageId 和 role 兼容字段。
     */
    private OpencodeSessionMessage toSessionMessage(SessionMessage message) {
        Map<String, Object> raw = objectMapper.convertValue(message, new TypeReference<Map<String, Object>>() {
        });
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(raw);
        String messageId = stringValue(raw.get("id"));
        if (messageId != null) {
            normalized.putIfAbsent("messageID", messageId);
            normalized.putIfAbsent("messageId", messageId);
        }
        String type = stringValue(raw.get("type"));
        normalized.putIfAbsent("role", "user".equals(type) ? "user" : "assistant");
        return new OpencodeSessionMessage(immutableWithoutNulls(normalized), partsFromContent(raw, messageId));
    }

    /**
     * 从 opencode message content 中抽取 part 列表，非列表内容按空列表处理。
     */
    private List<Map<String, Object>> partsFromContent(Map<String, Object> raw, String messageId) {
        Object content = raw.get("content");
        if (!(content instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> normalizePart((Map<?, ?>) item, messageId))
                .toList();
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
