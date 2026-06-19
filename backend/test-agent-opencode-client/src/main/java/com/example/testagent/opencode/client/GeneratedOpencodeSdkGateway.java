package com.example.testagent.opencode.client;

import com.example.opencode.sdk.ApiClient;
import com.example.opencode.sdk.api.EventApi;
import com.example.opencode.sdk.api.GlobalApi;
import com.example.opencode.sdk.api.SessionApi;
import com.example.opencode.sdk.model.SessionPromptAsyncRequest;
import com.example.opencode.sdk.model.SessionPromptRequestPartsInner;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.observability.TraceConstants;
import com.fasterxml.jackson.databind.JsonNode;
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

    @Override
    public Mono<OpencodeHealthResult> health(ExecutionNode node, String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        return new GlobalApi(apiClient)
                .globalHealth()
                .map(ignored -> new OpencodeHealthResult(true, node.baseUrl()));
    }

    @Override
    public Mono<OpencodeCancelResult> cancelSession(
            ExecutionNode node,
            SessionId sessionId,
            String directory,
            String workspace,
            String traceId) {
        ApiClient apiClient = apiClient(node, traceId);
        return new SessionApi(apiClient)
                .sessionAbort(sessionId.value(), directory, workspace)
                .map(cancelled -> new OpencodeCancelResult(Boolean.TRUE.equals(cancelled)));
    }

    @Override
    public Mono<OpencodeStartRunResult> startRun(
            ExecutionNode node,
            SessionId sessionId,
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
        pathParams.put("sessionID", sessionId.value());
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
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
                .eventSubscribeWithResponseSpec(directory, workspace)
                .bodyToFlux(JsonNode.class);
    }

    private ApiClient apiClient(ExecutionNode node, String traceId) {
        return new ApiClient()
                .setBasePath(node.baseUrl())
                .addDefaultHeader(TraceConstants.TRACE_ID_HEADER, traceId);
    }
}
