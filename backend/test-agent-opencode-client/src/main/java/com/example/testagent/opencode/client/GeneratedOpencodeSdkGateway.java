package com.example.testagent.opencode.client;

import com.example.opencode.sdk.ApiClient;
import com.example.opencode.sdk.api.EventApi;
import com.example.opencode.sdk.api.GlobalApi;
import com.example.opencode.sdk.api.SessionApi;
import com.example.opencode.sdk.model.SnapshotFileDiff;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.observability.TraceConstants;
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

    @Override
    public Mono<OpencodeHealthResult> health(ExecutionNode node, String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        return new GlobalApi(apiClient)
                .globalHealth()
                .map(ignored -> new OpencodeHealthResult(true, node.baseUrl()));
    }

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

    @Override
    public Flux<JsonNode> streamEvents(ExecutionNode node, String directory, String workspace, String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        return new EventApi(apiClient)
                .eventSubscribeWithResponseSpec(directory, optionalText(workspace))
                .bodyToFlux(JsonNode.class);
    }

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

    private ApiClient apiClient(ExecutionNode node, String traceId) {
        return new ApiClient()
                .setBasePath(node.baseUrl())
                .addDefaultHeader(TraceConstants.TRACE_ID_HEADER, traceId);
    }

    private MultiValueMap<String, String> queryParams(ApiClient apiClient, String directory, String workspace) {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        String optionalWorkspace = optionalText(workspace);
        if (optionalWorkspace != null) {
            queryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", optionalWorkspace));
        }
        return queryParams;
    }

    private String extractSessionId(JsonNode body) {
        if (body == null || body.path("id").asText().isBlank()) {
            throw new IllegalStateException("opencode create session response missing id");
        }
        return body.path("id").asText();
    }

    private OpencodeDiffFile toDiffFile(SnapshotFileDiff diff) {
        String status = diff.getStatus() == null ? "modified" : diff.getStatus().getValue();
        return new OpencodeDiffFile(
                diff.getFile(),
                diff.getPatch(),
                toLong(diff.getAdditions()),
                toLong(diff.getDeletions()),
                status);
    }

    private long toLong(BigDecimal value) {
        return value == null ? 0 : value.longValue();
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
