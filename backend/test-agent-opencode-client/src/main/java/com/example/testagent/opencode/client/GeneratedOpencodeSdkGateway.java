package com.example.testagent.opencode.client;

import com.example.opencode.sdk.ApiClient;
import com.example.opencode.sdk.api.EventApi;
import com.example.opencode.sdk.api.GlobalApi;
import com.example.opencode.sdk.api.SessionApi;
import com.example.opencode.sdk.model.SessionPromptAsyncRequest;
import com.example.opencode.sdk.model.SessionPromptRequestPartsInner;
import com.example.opencode.sdk.model.SnapshotFileDiff;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.observability.TraceConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
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
            String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        SessionPromptRequestPartsInner textPart = new SessionPromptRequestPartsInner()
                .type(SessionPromptRequestPartsInner.TypeEnum.TEXT)
                .text(prompt);
        SessionPromptAsyncRequest request = new SessionPromptAsyncRequest()
                .parts(List.of(textPart));
        // generated SessionApi 中参数包装类与 model 同名，直接走 generated ApiClient 可避免错误的内部类型遮蔽。
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
