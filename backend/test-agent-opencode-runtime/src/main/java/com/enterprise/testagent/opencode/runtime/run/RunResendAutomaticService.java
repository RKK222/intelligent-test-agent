package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.agent.runtime.AgentReplayableTurn;
import com.enterprise.testagent.agent.runtime.AgentReplayableTurnCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendPolicy;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendSourceTurnQuery;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 仅响应根 session.error 的定时 Run 自动重发，transport failure 与取消不会调用本服务。 */
@Service
public class RunResendAutomaticService implements RunRootSessionErrorObserver {

    private final RunResendApplicationService resendService;
    private final RunResendRepository resendRepository;
    private final RunResendSourceTurnQuery sourceTurnQuery;
    private final RunRuntimeStore runtimeStore;
    private final ConversationContextApplicationService contextService;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final Clock clock;

    public RunResendAutomaticService(
            RunResendApplicationService resendService,
            RunResendRepository resendRepository,
            RunResendSourceTurnQuery sourceTurnQuery,
            RunRuntimeStore runtimeStore,
            ConversationContextApplicationService contextService,
            AgentRuntimeRegistry runtimeRegistry,
            Clock clock) {
        this.resendService = Objects.requireNonNull(resendService);
        this.resendRepository = Objects.requireNonNull(resendRepository);
        this.sourceTurnQuery = Objects.requireNonNull(sourceTurnQuery);
        this.runtimeStore = Objects.requireNonNull(runtimeStore);
        this.contextService = Objects.requireNonNull(contextService);
        this.runtimeRegistry = Objects.requireNonNull(runtimeRegistry);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void onRootSessionError(Run sourceRun, RunEventDraft terminalEvent) {
        schedule(sourceRun, terminalEvent.traceId());
    }

    /** 重复终态事件按 source Run 唯一约束幂等；超过 3 次自动额度直接返回空。 */
    public Optional<RunResend> schedule(Run sourceRun, String traceId) {
        Objects.requireNonNull(sourceRun, "sourceRun must not be null");
        if (sourceRun.sourceType() != ConversationSourceType.SCHEDULED_TASK
                || sourceRun.triggeredByUserId() == null
                || sourceRun.status() == RunStatus.CANCELLED) {
            return Optional.empty();
        }
        Optional<RunResend> existing = resendRepository.findBySourceRunId(sourceRun.runId());
        if (existing.isPresent()) {
            return existing;
        }
        if (resendRepository.findActiveBySession(sourceRun.sessionId()).isPresent()) {
            return Optional.empty();
        }
        RunResend previous = resendRepository.findByReplacementRunId(sourceRun.runId()).orElse(null);
        int completedAutomaticAttempts = previous == null ? 0 : previous.automaticAttempt();
        if (!RunResendPolicy.canScheduleAutomatic(completedAutomaticAttempts)) {
            return Optional.empty();
        }
        int automaticAttempt = completedAutomaticAttempts + 1;
        int totalAttempt = previous == null ? 1 : previous.totalAttempt() + 1;
        String sourceMessageId = sourceTurnQuery.findLatestUserTurn(sourceRun.sessionId())
                .filter(turn -> turn.runId().equals(sourceRun.runId()))
                .map(turn -> turn.remoteMessageId())
                .orElseGet(() -> runtimeStore.findInput(sourceRun.runId())
                        .map(input -> input.messageId())
                        .orElse(null));
        if (sourceMessageId == null || sourceMessageId.isBlank()) {
            return Optional.empty();
        }
        String agentId = runtimeRegistry.defaultAgentId();
        var issued = contextService.bootstrap(
                sourceRun.triggeredByUserId(), agentId, sourceRun.sessionId(), traceId);
        if (issued.context() == null || issued.context().remoteSessionId() == null) {
            return Optional.empty();
        }
        AgentRuntime runtime = runtimeRegistry.require(agentId);
        AgentReplayableTurn replayable = runtime.loadReplayableTurn(new AgentReplayableTurnCommand(
                        issued.context().executionNodeSnapshot(),
                        issued.context().remoteSessionId(),
                        issued.context().trustedWorkspaceRoot(),
                        null,
                        sourceMessageId,
                        traceId))
                .block();
        if (replayable == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return Optional.of(resendService.reserveAutomatic(
                sourceRun.triggeredByUserId(),
                agentId,
                sourceRun,
                sourceMessageId,
                replayable,
                issued.context().linuxServerId(),
                totalAttempt,
                automaticAttempt,
                now.plus(RunResendPolicy.automaticDelay(automaticAttempt)),
                "auto-resend:" + sourceRun.runId().value(),
                traceId));
    }
}
