package com.icbc.testagent.opencode.client;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * generated SDK 内部适配端口，便于 facade 测试覆盖错误、超时和事件流转换。
 */
public interface OpencodeSdkGateway {

    /**
     * 直接调用 generated health API，并返回平台健康投影。
     */
    Mono<OpencodeHealthResult> health(ExecutionNode node, String traceId);

    /**
     * 调用远端创建 session API，directory 必传，workspace 为空时不传 query。
     */
    Mono<OpencodeCreateSessionResult> createSession(
            ExecutionNode node,
            String directory,
            String workspace,
            String title,
            String traceId);

    /**
     * 调用远端 session abort API，返回远端布尔响应的稳定包装。
     */
    Mono<OpencodeCancelResult> cancelSession(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String traceId);

    /**
     * 调用 prompt_async API，prompt parts 和运行态选择由 gateway 转成远端 JSON body。
     */
    Mono<OpencodeStartRunResult> startRun(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String prompt,
            java.util.List<OpencodePromptPart> parts,
            String messageId,
            String agent,
            String modelProviderId,
            String modelId,
            String variant,
            String traceId);

    /**
     * 订阅远端事件 SSE，保持 JsonNode 原文供 facade 做平台事件映射。
     */
    Flux<JsonNode> streamEvents(ExecutionNode node, String directory, String workspace, String traceId);

    /**
     * 调用远端 Diff API，并返回平台 Diff DTO。
     */
    Mono<OpencodeDiffResult> getDiff(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String messageId,
            String traceId);

    /**
     * 调用远端 revert API，可按 message 或 message+part 拒绝 Diff。
     */
    Mono<OpencodeRejectDiffResult> rejectDiff(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String messageId,
            String partId,
            String traceId);

    /**
     * 调用受控 runtime API，query 和 body 由上层策略提前校验。
     */
    Mono<OpencodeRuntimeResult> runtime(
            ExecutionNode node,
            String method,
            String path,
            String directory,
            String workspace,
            java.util.Map<String, String> query,
            Object body,
            String traceId);

    /**
     * 通过 generated ApiClient 读取远端标准 session messages。
     */
    Mono<OpencodeSessionMessagesResult> sessionMessages(
            ExecutionNode node,
            String opencodeSessionId,
            int limit,
            String order,
            String cursor,
            String traceId);
}
