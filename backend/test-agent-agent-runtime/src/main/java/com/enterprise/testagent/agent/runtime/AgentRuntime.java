package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.event.RunEventDraft;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 多 agent 运行时统一接口，所有实现必须把自身协议适配为平台稳定模型。
 */
public interface AgentRuntime {

    /**
     * 返回 URL 中使用的稳定 agent 标志，例如 {@code opencode}。
     */
    String agentId();

    /**
     * 生成远端用户消息的稳定派发锚点；未适配的 agent 保持平台原有 UUID 格式。
     */
    default String createDispatchMessageId() {
        return RuntimeIdGenerator.messageId();
    }

    /**
     * 创建远端 agent 会话，并返回远端会话 ID。
     */
    default Mono<AgentCreateSessionResult> createSession(AgentCreateSessionCommand command) {
        return Mono.error(unsupported("createSession"));
    }

    /**
     * 校验远端 agent 会话是否仍存在。
     */
    default Mono<Boolean> sessionExists(AgentSessionExistsCommand command) {
        return Mono.error(unsupported("sessionExists"));
    }

    /**
     * 启动一次远端运行。
     */
    default Mono<AgentStartRunResult> startRun(AgentStartRunCommand command) {
        return Mono.error(unsupported("startRun"));
    }

    /**
     * 取消远端会话当前运行。
     */
    default Mono<AgentCancelResult> cancelSession(AgentCancelCommand command) {
        return Mono.error(unsupported("cancelSession"));
    }

    /**
     * 订阅远端事件流，输出平台 RunEventDraft。
     */
    default Flux<RunEventDraft> streamRunEvents(AgentStreamEventsCommand command) {
        return Flux.error(unsupported("streamRunEvents"));
    }

    /**
     * 打开带真实连接 ready 的事件流；未显式实现时拒绝启动，不能用空信号伪装握手完成。
     */
    default AgentEventStream openRunEventStream(AgentStreamEventsCommand command) {
        return new AgentEventStream(Mono.error(unsupported("openRunEventStream")), streamRunEvents(command));
    }

    /**
     * 查询远端 Diff。
     */
    default Mono<AgentDiffResult> getDiff(AgentDiffCommand command) {
        return Mono.error(unsupported("getDiff"));
    }

    /**
     * 拒绝远端 Diff。
     */
    default Mono<AgentRejectDiffResult> rejectDiff(AgentRejectDiffCommand command) {
        return Mono.error(unsupported("rejectDiff"));
    }

    /**
     * 受控代理 agent runtime JSON API。
     */
    default Mono<AgentRuntimeResult> runtime(AgentRuntimeCommand command) {
        return Mono.error(unsupported("runtime"));
    }

    /**
     * 读取远端会话消息投影。
     */
    default Mono<AgentSessionMessagesResult> sessionMessages(AgentSessionMessagesCommand command) {
        return Mono.error(unsupported("sessionMessages"));
    }

    /** 读取一个可精确重放的远端用户轮次。 */
    default Mono<AgentReplayableTurn> loadReplayableTurn(AgentReplayableTurnCommand command) {
        return Mono.error(unsupported("loadReplayableTurn"));
    }

    /** 从用户消息边界执行远端完整回退。 */
    default Mono<AgentRevertTurnResult> revertTurn(AgentRevertTurnCommand command) {
        return Mono.error(unsupported("revertTurn"));
    }

    /** 仅用于确定未投递后的安全补偿。 */
    default Mono<AgentUnrevertTurnResult> unrevertTurn(AgentUnrevertTurnCommand command) {
        return Mono.error(unsupported("unrevertTurn"));
    }

    /** 用稳定新消息 ID 探测投递受理事实。 */
    default Mono<AgentMessageProbeResult> probeMessage(AgentMessageProbeCommand command) {
        return Mono.error(unsupported("probeMessage"));
    }

    /**
     * 为占位实现提供统一未实现异常。
     */
    private IllegalStateException unsupported(String operation) {
        return new IllegalStateException(agentId() + " runtime does not implement " + operation);
    }
}
