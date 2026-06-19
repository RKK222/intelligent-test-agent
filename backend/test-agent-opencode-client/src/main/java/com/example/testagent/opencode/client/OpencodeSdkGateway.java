package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.session.SessionId;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * generated SDK 内部适配端口，便于 facade 测试覆盖错误、超时和事件流转换。
 */
public interface OpencodeSdkGateway {

    Mono<OpencodeHealthResult> health(ExecutionNode node, String traceId);

    Mono<OpencodeCancelResult> cancelSession(
            ExecutionNode node,
            SessionId sessionId,
            String directory,
            String workspace,
            String traceId);

    Mono<OpencodeStartRunResult> startRun(
            ExecutionNode node,
            SessionId sessionId,
            String directory,
            String workspace,
            String prompt,
            String traceId);

    Flux<JsonNode> streamEvents(ExecutionNode node, String directory, String workspace, String traceId);
}
