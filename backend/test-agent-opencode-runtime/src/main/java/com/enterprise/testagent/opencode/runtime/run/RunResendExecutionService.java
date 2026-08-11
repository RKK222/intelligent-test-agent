package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.agent.runtime.AgentMessageProbeCommand;
import com.enterprise.testagent.agent.runtime.AgentMessageProbeResult;
import com.enterprise.testagent.agent.runtime.AgentRevertTurnCommand;
import com.enterprise.testagent.agent.runtime.AgentRevertTurnResult;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentUnrevertTurnCommand;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendDetailCleanupPort;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.domain.run.RunResendReplayInput;
import com.enterprise.testagent.domain.run.RunResendReplayInputStore;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub.SessionMessageChange;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub.SessionMessageChangeType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 执行一条持久重发记录，并在所有不确定窗口使用稳定消息 ID 恢复。 */
@Service
public class RunResendExecutionService {

    private static final Duration LEASE_TTL = Duration.ofMinutes(2);

    private final RunResendRepository resendRepository;
    private final RunResendReplayInputStore inputStore;
    private final RunRepository runRepository;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final RunApplicationService runApplicationService;
    private final RunResendDetailCleanupPort cleanupPort;
    private final ConversationContextApplicationService contextService;
    private final RunEventAppender eventAppender;
    private final Clock clock;
    private RunRuntimeStore runRuntimeStore;
    private SessionMessageRealtimeHub sessionMessageRealtimeHub;

    public RunResendExecutionService(
            RunResendRepository resendRepository,
            RunResendReplayInputStore inputStore,
            RunRepository runRepository,
            AgentRuntimeRegistry runtimeRegistry,
            RunApplicationService runApplicationService,
            RunResendDetailCleanupPort cleanupPort,
            ConversationContextApplicationService contextService,
            RunEventAppender eventAppender,
            Clock clock) {
        this.resendRepository = Objects.requireNonNull(resendRepository);
        this.inputStore = Objects.requireNonNull(inputStore);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.runtimeRegistry = Objects.requireNonNull(runtimeRegistry);
        this.runApplicationService = Objects.requireNonNull(runApplicationService);
        this.cleanupPort = Objects.requireNonNull(cleanupPort);
        this.contextService = Objects.requireNonNull(contextService);
        this.eventAppender = Objects.requireNonNull(eventAppender);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Redis 运行态为可选方法注入，保持既有纯单元测试构造器稳定。 */
    @Autowired(required = false)
    void configureRunRuntimeStore(RunRuntimeStore runRuntimeStore) {
        this.runRuntimeStore = runRuntimeStore;
    }

    /** 后端消息变更广播为可选方法注入，保持既有纯单元测试构造器稳定。 */
    @Autowired(required = false)
    void configureSessionMessageRealtimeHub(SessionMessageRealtimeHub sessionMessageRealtimeHub) {
        this.sessionMessageRealtimeHub = sessionMessageRealtimeHub;
    }

    public RunResend execute(RunResendId resendId) {
        RunResend waiting = resendRepository.findById(resendId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "重发记录不存在"));
        if (waiting.status() == RunResendStatus.DISPATCHED || waiting.status() == RunResendStatus.CANCELLED) {
            return waiting;
        }
        RunResendReplayInput input = inputStore.find(waiting.replacementRunId()).orElse(null);
        if (input == null) {
            return failBeforeRevert(waiting, "重发输入已过期");
        }
        if (waiting.status() == RunResendStatus.REVERTED) {
            return recoverAfterRevert(waiting, input);
        }
        if (waiting.status() == RunResendStatus.REVERTING) {
            return recoverDuringRevert(waiting, input);
        }
        if (waiting.status() != RunResendStatus.WAITING) {
            return waiting;
        }
        // 可信上下文和目标路由先完成校验；失败时记录仍保持 WAITING，尚未进入不可逆阶段。
        ConversationContextApplicationService.IssuedConversationContext issued = contextService.bootstrap(
                waiting.ownerUserId(), runtimeRegistry.defaultAgentId(), waiting.sessionId(), waiting.traceId());
        requireTargetServer(waiting, issued);
        Instant now = clock.instant();
        RunResend reverting = waiting.startReverting(
                RuntimeIdGenerator.messageId(), now.plus(LEASE_TTL), now);
        if (!resendRepository.saveIfStatus(reverting, RunResendStatus.WAITING)) {
            return resendRepository.findById(resendId).orElse(reverting);
        }
        return revertAndDispatch(reverting, input, issued, false);
    }

    private RunResend recoverDuringRevert(RunResend stale, RunResendReplayInput input) {
        Instant now = clock.instant();
        if (stale.leaseUntil() != null && stale.leaseUntil().isAfter(now)) {
            return stale;
        }
        var issued = contextService.bootstrap(
                stale.ownerUserId(), runtimeRegistry.defaultAgentId(), stale.sessionId(), stale.traceId());
        requireTargetServer(stale, issued);
        RunResend renewed = stale.renewRevertingLease(
                RuntimeIdGenerator.messageId(), now.plus(LEASE_TTL), now);
        if (!resendRepository.saveIfStatusAndLease(
                renewed, RunResendStatus.REVERTING, stale.leaseToken())) {
            return resendRepository.findById(stale.resendId()).orElse(stale);
        }
        return revertAndDispatch(renewed, input, issued, true);
    }

    private RunResend revertAndDispatch(
            RunResend reverting,
            RunResendReplayInput input,
            ConversationContextApplicationService.IssuedConversationContext issued,
            boolean recovering) {
        AgentRuntime runtime = runtimeRegistry.require(runtimeRegistry.defaultAgentId());
        AgentRevertTurnResult revertedResult = runtime.revertTurn(new AgentRevertTurnCommand(
                        issued.context().executionNodeSnapshot(),
                        issued.context().remoteSessionId(),
                        issued.context().trustedWorkspaceRoot(),
                        null,
                        reverting.sourceRemoteMessageId(),
                        reverting.traceId()))
                .block();
        if (revertedResult == null || !revertedResult.reverted()) {
            if (recovering) {
                // 旧请求是否已应用未知；保留 REVERTING 与锁，后续恢复继续使用同一边界探测。
                return reverting;
            }
            return failBeforeRevert(reverting, "远端未受理撤销");
        }
        RunResend reverted = reverting.markReverted(clock.instant());
        if (!resendRepository.saveIfStatus(reverted, RunResendStatus.REVERTING)) {
            return resendRepository.findById(reverting.resendId()).orElse(reverted);
        }
        return dispatchOrRecover(reverted, input, issued, runtime);
    }

    private RunResend recoverAfterRevert(RunResend resend, RunResendReplayInput input) {
        ConversationContextApplicationService.IssuedConversationContext issued = contextService.bootstrap(
                resend.ownerUserId(), runtimeRegistry.defaultAgentId(), resend.sessionId(), resend.traceId());
        requireTargetServer(resend, issued);
        AgentRuntime runtime = runtimeRegistry.require(runtimeRegistry.defaultAgentId());
        AgentMessageProbeResult probe = probeReplacement(resend, issued, runtime);
        if (probe.present()) {
            return commitStarted(resend);
        }
        // 上次 unrevert 回包也可能丢失；确认替代消息不存在后再次 revert，确保模型上下文确实处于回退态。
        AgentRevertTurnResult ensured = runtime.revertTurn(new AgentRevertTurnCommand(
                        issued.context().executionNodeSnapshot(),
                        issued.context().remoteSessionId(),
                        issued.context().trustedWorkspaceRoot(),
                        null,
                        resend.sourceRemoteMessageId(),
                        resend.traceId()))
                .block();
        if (ensured == null || !ensured.reverted()) {
            return resend;
        }
        return dispatchOrRecover(resend, input, issued, runtime);
    }

    private RunResend dispatchOrRecover(
            RunResend reverted,
            RunResendReplayInput input,
            ConversationContextApplicationService.IssuedConversationContext issued,
            AgentRuntime runtime) {
        AgentMessageProbeResult probe = probeReplacement(reverted, issued, runtime);
        if (probe.present()) {
            return commitStarted(reverted);
        }
        try {
            Run sourceRun = runRepository.findById(reverted.sourceRunId())
                    .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "源 Run 不存在"));
            runApplicationService.startResendRun(
                    reverted.ownerUserId(),
                    startInput(reverted, input, issued.contextToken()),
                    reverted.replacementRunId(),
                    sourceRun.sourceType(),
                    sourceRun.sourceRefId(),
                    reverted.traceId());
            return commitStarted(reverted);
        } catch (RuntimeException failure) {
            return compensateKnownFailure(reverted, issued, runtime, failure);
        }
    }

    private RunResend compensateKnownFailure(
            RunResend reverted,
            ConversationContextApplicationService.IssuedConversationContext issued,
            AgentRuntime runtime,
            RuntimeException failure) {
        AgentMessageProbeResult secondProbe;
        try {
            secondProbe = runtime.probeMessage(new AgentMessageProbeCommand(
                            issued.context().executionNodeSnapshot(),
                            issued.context().remoteSessionId(),
                            reverted.replacementRemoteMessageId(),
                            reverted.traceId()))
                    .block();
        } catch (RuntimeException unknown) {
            // 状态未知时保留 REVERTED 与会话锁，下一轮恢复任务继续探测，绝不重复发送或 unrevert。
            throw unknown;
        }
        if (secondProbe == null) {
            throw unknownDeliveryState(reverted);
        }
        if (secondProbe.present()) {
            return commitStarted(reverted);
        }
        var unreverted = runtime.unrevertTurn(new AgentUnrevertTurnCommand(
                        issued.context().executionNodeSnapshot(),
                        issued.context().remoteSessionId(),
                        issued.context().trustedWorkspaceRoot(),
                        null,
                        reverted.traceId()))
                .block();
        if (unreverted == null || !unreverted.unreverted()) {
            throw unknownDeliveryState(reverted);
        }
        return fail(reverted, "替代消息投递失败", failure);
    }

    private AgentMessageProbeResult probeReplacement(
            RunResend resend,
            ConversationContextApplicationService.IssuedConversationContext issued,
            AgentRuntime runtime) {
        AgentMessageProbeResult result = runtime.probeMessage(new AgentMessageProbeCommand(
                        issued.context().executionNodeSnapshot(),
                        issued.context().remoteSessionId(),
                        resend.replacementRemoteMessageId(),
                        resend.traceId()))
                .block();
        if (result == null) {
            throw unknownDeliveryState(resend);
        }
        return result;
    }

    private PlatformException unknownDeliveryState(RunResend resend) {
        return new PlatformException(
                ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                "替代消息投递状态未知，将保留会话锁等待恢复",
                Map.of("resendId", resend.resendId().value()));
    }

    private RunResend commitStarted(RunResend reverted) {
        Instant acceptedAt = clock.instant();
        cleanupPort.purgeSourceRun(reverted.sourceRunId(), reverted.sessionId(), acceptedAt);
        if (runRuntimeStore != null) {
            runRuntimeStore.purgeDetailsAfterResend(reverted.sourceRunId());
        }
        RunResend dispatched = reverted.markDispatched(acceptedAt);
        if (!resendRepository.saveIfStatus(dispatched, RunResendStatus.REVERTED)) {
            // 清理动作可幂等重放，但只有赢得状态迁移的执行者可以解锁并发布 started，避免重复接管事件。
            return resendRepository.findById(reverted.resendId()).orElse(reverted);
        }
        resendRepository.deleteSessionLock(dispatched.sessionId(), dispatched.resendId());
        inputStore.delete(dispatched.replacementRunId());
        eventAppender.append(new RunEventDraft(
                dispatched.replacementRunId(),
                RunEventType.RUN_RESEND_STARTED,
                dispatched.traceId(),
                acceptedAt,
                RunResendApplicationService.eventPayload(dispatched)), RunStorageMode.LEGACY_FULL);
        if (sessionMessageRealtimeHub != null) {
            // started 事实和消息清理均成功后再唤醒当前会话，前端读取时只会看到已提交快照。
            sessionMessageRealtimeHub.publishAfterCommit(new SessionMessageChange(
                    dispatched.sessionId(), dispatched.sourceRunId(), dispatched.replacementRunId(),
                    SessionMessageChangeType.RESEND_STARTED,
                    acceptedAt, dispatched.traceId(), acceptedAt));
        }
        return dispatched;
    }

    private RunResend failBeforeRevert(RunResend resend, String message) {
        return fail(resend, message, null);
    }

    private RunResend fail(RunResend resend, String message, RuntimeException failure) {
        Instant failedAt = clock.instant();
        RunResend failed = resend.fail(message, failedAt);
        if (!resendRepository.saveIfStatus(failed, resend.status())) {
            // 取消、恢复或其它执行者已经推进状态时，当前失败分支不得解锁或覆盖替代 Run。
            return resendRepository.findById(resend.resendId()).orElse(resend);
        }
        runRepository.findById(failed.replacementRunId())
                .filter(run -> run.status() == RunStatus.PENDING)
                .ifPresent(run -> runRepository.saveIfStatus(run.fail(failedAt), RunStatus.PENDING));
        cleanupPort.purgePendingReplacementMessage(
                failed.replacementRunId(), failed.sessionId(), failedAt);
        resendRepository.deleteSessionLock(failed.sessionId(), failed.resendId());
        inputStore.delete(failed.replacementRunId());
        eventAppender.append(new RunEventDraft(
                failed.replacementRunId(),
                RunEventType.RUN_RESEND_FAILED,
                failed.traceId(),
                failedAt,
                RunResendApplicationService.eventPayload(failed)), RunStorageMode.LEGACY_FULL);
        if (sessionMessageRealtimeHub != null) {
            sessionMessageRealtimeHub.publishAfterCommit(new SessionMessageChange(
                    failed.sessionId(), failed.sourceRunId(), failed.replacementRunId(),
                    SessionMessageChangeType.RESEND_RESTORED,
                    failedAt, failed.traceId(), failedAt));
        }
        return failed;
    }

    private StartRunInput startInput(RunResend resend, RunResendReplayInput input, String contextToken) {
        String model = input.modelProviderId() == null
                ? input.modelId()
                : input.modelProviderId() + "/" + input.modelId();
        return new StartRunInput(
                resend.sessionId(),
                input.prompt(),
                input.parts().stream().map(this::promptPart).toList(),
                input.messageId(),
                input.agent(),
                model,
                input.variant(),
                input.agent(),
                null,
                null,
                contextToken,
                resend.clientRequestId());
    }

    private StartRunInput.PromptPart promptPart(Map<String, Object> part) {
        String type = text(part, "type");
        Map<String, Object> source = map(part.get("source"));
        return switch (type) {
            case "text" -> StartRunInput.PromptPart.text(text(part, "text"));
            case "file" -> new StartRunInput.PromptPart(
                    "file", null, null, text(part, "filename"), text(part, "mime"), null,
                    text(part, "url"), null, null, null, null, source, Map.of());
            case "agent" -> new StartRunInput.PromptPart(
                    "agent", null, null, text(part, "agentName"), null, null, null,
                    text(part, "agentName"), null, null, null, source, Map.of());
            case "subtask" -> new StartRunInput.PromptPart(
                    "subtask", text(part, "text"), null, text(part, "agentName"), null, null, null,
                    text(part, "agentName"), null, text(part, "filename"), null, Map.of(), source);
            default -> throw new PlatformException(ErrorCode.VALIDATION_ERROR, "重发输入包含不支持的 part 类型");
        };
    }

    private void requireTargetServer(
            RunResend resend,
            ConversationContextApplicationService.IssuedConversationContext issued) {
        if (issued.context() == null
                || !resend.targetLinuxServerId().equals(issued.context().linuxServerId())
                || issued.context().remoteSessionId() == null) {
            throw new PlatformException(ErrorCode.CONFLICT, "重发目标服务器或远端会话已变化");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
