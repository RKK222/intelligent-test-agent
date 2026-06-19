package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * generated SDK 内部适配端口，便于 facade 测试覆盖错误、超时和事件流转换。
 */
public interface OpencodeSdkGateway {

    Mono<OpencodeHealthResult> health(ExecutionNode node, String traceId);

    Mono<OpencodeCreateSessionResult> createSession(
            ExecutionNode node,
            String directory,
            String workspace,
            String title,
            String traceId);

    Mono<OpencodeCancelResult> cancelSession(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String traceId);

    Mono<OpencodeStartRunResult> startRun(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String prompt,
            String traceId);

    Flux<JsonNode> streamEvents(ExecutionNode node, String directory, String workspace, String traceId);

    Mono<OpencodeDiffResult> getDiff(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String messageId,
            String traceId);

    Mono<OpencodeRejectDiffResult> rejectDiff(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String messageId,
            String partId,
            String traceId);
}
