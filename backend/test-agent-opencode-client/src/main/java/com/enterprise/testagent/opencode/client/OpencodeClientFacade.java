package com.enterprise.testagent.opencode.client;

import com.enterprise.testagent.domain.event.RunEventDraft;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 业务侧访问 opencode server 的唯一门面，不暴露 generated SDK API 或 DTO。
 */
public interface OpencodeClientFacade {

    /**
     * 检查命令指定执行节点的 opencode 健康状态。
     */
    Mono<OpencodeHealthResult> health(OpencodeHealthCommand command);

    /**
     * 创建远端 opencode session，并返回平台稳定的 session id 结果。
     */
    Mono<OpencodeCreateSessionResult> createSession(OpencodeCreateSessionCommand command);

    /**
     * 校验远端 opencode session 是否仍存在；404 映射为 false。
     */
    Mono<Boolean> sessionExists(OpencodeSessionExistsCommand command);

    /**
     * 取消远端 opencode session，并返回远端是否接受取消。
     */
    Mono<OpencodeCancelResult> cancelSession(OpencodeCancelCommand command);

    /**
     * 向远端 opencode session 发送 prompt_async，启动一次平台 Run。
     */
    Mono<OpencodeStartRunResult> startRun(OpencodeStartRunCommand command);

    /**
     * 通过原生 session command 启动技能任务；调用在后端后台持续等待，浏览器断开不影响执行。
     */
    Mono<OpencodeStartRunResult> startCommand(OpencodeStartCommand command);

    /**
     * 订阅远端事件流，并输出已经映射好的平台 RunEventDraft。
     */
    Flux<RunEventDraft> streamRunEvents(OpencodeStreamEventsCommand command);

    /**
     * 打开带真实 SSE ready 信号的 RunEvent 流；未实现时拒绝以空信号伪装连接成功。
     */
    default OpencodeRunEventStream openRunEventStream(OpencodeStreamEventsCommand command) {
        return new OpencodeRunEventStream(
                Mono.error(new UnsupportedOperationException("observable event stream is not implemented")),
                streamRunEvents(command));
    }

    /**
     * 查询远端 session Diff，结果不得包含 generated SDK DTO。
     */
    Mono<OpencodeDiffResult> getDiff(OpencodeDiffCommand command);

    /**
     * 拒绝远端 Diff，对应 opencode session revert 能力。
     */
    Mono<OpencodeRejectDiffResult> rejectDiff(OpencodeRejectDiffCommand command);

    /**
     * 受控调用 opencode runtime HTTP API，并返回稳定 JSON projection。
     */
    Mono<OpencodeRuntimeResult> runtime(OpencodeRuntimeCommand command);

    /**
     * 读取远端 projected messages，用于平台刷新或断线后的消息恢复。
     */
    Mono<OpencodeSessionMessagesResult> sessionMessages(OpencodeSessionMessagesCommand command);
}
