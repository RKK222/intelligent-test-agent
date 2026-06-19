package com.example.testagent.opencode.client;

import com.example.opencode.sdk.ApiClient;
import com.example.opencode.sdk.api.EventApi;
import com.example.opencode.sdk.api.GlobalApi;
import com.example.opencode.sdk.api.SessionApi;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.observability.TraceConstants;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
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
