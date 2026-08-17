package com.enterprise.testagent.opencode.runtime.protectedagent;

import com.enterprise.testagent.agent.runtime.AgentCancelCommand;
import com.enterprise.testagent.agent.runtime.AgentCancelResult;
import com.enterprise.testagent.agent.runtime.AgentCreateSessionCommand;
import com.enterprise.testagent.agent.runtime.AgentCreateSessionResult;
import com.enterprise.testagent.agent.runtime.AgentDiffCommand;
import com.enterprise.testagent.agent.runtime.AgentDiffResult;
import com.enterprise.testagent.agent.runtime.AgentEventStream;
import com.enterprise.testagent.agent.runtime.AgentMessageProbeCommand;
import com.enterprise.testagent.agent.runtime.AgentMessageProbeResult;
import com.enterprise.testagent.agent.runtime.AgentRejectDiffCommand;
import com.enterprise.testagent.agent.runtime.AgentRejectDiffResult;
import com.enterprise.testagent.agent.runtime.AgentReplayableTurn;
import com.enterprise.testagent.agent.runtime.AgentReplayableTurnCommand;
import com.enterprise.testagent.agent.runtime.AgentRevertTurnCommand;
import com.enterprise.testagent.agent.runtime.AgentRevertTurnResult;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.agent.runtime.AgentSessionExistsCommand;
import com.enterprise.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.enterprise.testagent.agent.runtime.AgentSessionMessagesResult;
import com.enterprise.testagent.agent.runtime.AgentStartRunCommand;
import com.enterprise.testagent.agent.runtime.AgentStartRunResult;
import com.enterprise.testagent.agent.runtime.AgentStreamEventsCommand;
import com.enterprise.testagent.agent.runtime.AgentUnrevertTurnCommand;
import com.enterprise.testagent.agent.runtime.AgentUnrevertTurnResult;
import com.enterprise.testagent.agent.runtime.OpencodeAgentRuntime;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.event.RunEventDraft;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 服务器侧受保护 OpenCode 运行时。
 *
 * <p>协议复用既有 OpenCode 适配器，但所有含目录的调用都从 Redis 恢复服务器隔离目录，绝不使用
 * 本地客户端上报的绝对路径。</p>
 */
@Service
public class ProtectedOpencodeAgentRuntime implements AgentRuntime {

    public static final String AGENT_ID = "protected-opencode";
    private static final Duration DIRECTORY_TTL = Duration.ofDays(7);

    private final OpencodeAgentRuntime delegate;
    private final ProtectedAgentSessionDirectoryStore directoryStore;

    public ProtectedOpencodeAgentRuntime(
            OpencodeAgentRuntime delegate,
            ProtectedAgentSessionDirectoryStore directoryStore) {
        this.delegate = delegate;
        this.directoryStore = directoryStore;
    }

    @Override
    public String agentId() {
        return AGENT_ID;
    }

    @Override
    public String createDispatchMessageId() {
        return delegate.createDispatchMessageId();
    }

    @Override
    public Mono<AgentCreateSessionResult> createSession(AgentCreateSessionCommand command) {
        return delegate.createSession(command).doOnNext(result -> directoryStore.save(
                result.remoteSessionId(), requireDirectory(command.directory()), DIRECTORY_TTL));
    }

    @Override
    public Mono<Boolean> sessionExists(AgentSessionExistsCommand command) {
        if (directoryStore.find(command.remoteSessionId()).isEmpty()) {
            return Mono.just(false);
        }
        return delegate.sessionExists(command);
    }

    @Override
    public Mono<AgentStartRunResult> startRun(AgentStartRunCommand command) {
        return delegate.startRun(new AgentStartRunCommand(
                command.node(), command.remoteSessionId(), directory(command.remoteSessionId()), command.workspace(),
                command.prompt(), command.parts(), command.messageId(), command.agent(), command.system(),
                command.modelProviderId(), command.modelId(), command.variant(), command.tools(), command.command(),
                command.arguments(), command.traceId()));
    }

    @Override
    public Mono<AgentCancelResult> cancelSession(AgentCancelCommand command) {
        return delegate.cancelSession(new AgentCancelCommand(
                command.node(), command.remoteSessionId(), directory(command.remoteSessionId()),
                command.workspace(), command.traceId()));
    }

    @Override
    public Flux<RunEventDraft> streamRunEvents(AgentStreamEventsCommand command) {
        return delegate.streamRunEvents(streamCommand(command));
    }

    @Override
    public AgentEventStream openRunEventStream(AgentStreamEventsCommand command) {
        return delegate.openRunEventStream(streamCommand(command));
    }

    @Override
    public Mono<AgentDiffResult> getDiff(AgentDiffCommand command) {
        return delegate.getDiff(new AgentDiffCommand(
                command.node(), command.remoteSessionId(), directory(command.remoteSessionId()), command.workspace(),
                command.messageId(), command.traceId()));
    }

    @Override
    public Mono<AgentRejectDiffResult> rejectDiff(AgentRejectDiffCommand command) {
        return delegate.rejectDiff(new AgentRejectDiffCommand(
                command.node(), command.remoteSessionId(), directory(command.remoteSessionId()), command.workspace(),
                command.messageId(), command.partId(), command.traceId()));
    }

    @Override
    public Mono<AgentRuntimeResult> runtime(AgentRuntimeCommand command) {
        return delegate.runtime(command);
    }

    @Override
    public Mono<AgentSessionMessagesResult> sessionMessages(AgentSessionMessagesCommand command) {
        return delegate.sessionMessages(command);
    }

    @Override
    public Mono<AgentReplayableTurn> loadReplayableTurn(AgentReplayableTurnCommand command) {
        return delegate.loadReplayableTurn(new AgentReplayableTurnCommand(
                command.node(), command.remoteSessionId(), directory(command.remoteSessionId()), command.workspace(),
                command.messageId(), command.traceId()));
    }

    @Override
    public Mono<AgentRevertTurnResult> revertTurn(AgentRevertTurnCommand command) {
        return delegate.revertTurn(new AgentRevertTurnCommand(
                command.node(), command.remoteSessionId(), directory(command.remoteSessionId()), command.workspace(),
                command.messageId(), command.traceId()));
    }

    @Override
    public Mono<AgentUnrevertTurnResult> unrevertTurn(AgentUnrevertTurnCommand command) {
        return delegate.unrevertTurn(new AgentUnrevertTurnCommand(
                command.node(), command.remoteSessionId(), directory(command.remoteSessionId()),
                command.workspace(), command.traceId()));
    }

    @Override
    public Mono<AgentMessageProbeResult> probeMessage(AgentMessageProbeCommand command) {
        return delegate.probeMessage(command);
    }

    private AgentStreamEventsCommand streamCommand(AgentStreamEventsCommand command) {
        return new AgentStreamEventsCommand(
                command.node(), command.runId(), command.remoteSessionId(), directory(command.remoteSessionId()),
                command.workspace(), command.traceId());
    }

    private String directory(String remoteSessionId) {
        return directoryStore.find(remoteSessionId)
                .map(this::requireDirectory)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                        "受保护 Agent 会话目录已失效",
                        Map.of("remoteSessionId", remoteSessionId)));
    }

    private String requireDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "受保护 Agent 缺少服务器隔离目录");
        }
        return directory.trim();
    }
}
