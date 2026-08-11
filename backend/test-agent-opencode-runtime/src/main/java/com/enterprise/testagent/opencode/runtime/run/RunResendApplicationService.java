package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.agent.runtime.AgentPromptPart;
import com.enterprise.testagent.agent.runtime.AgentReplayableTurn;
import com.enterprise.testagent.agent.runtime.AgentReplayableTurnCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.run.ConversationRunContext;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.domain.run.RunResendPolicy;
import com.enterprise.testagent.domain.run.RunResendReplayInput;
import com.enterprise.testagent.domain.run.RunResendReplayInputStore;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendSourceTurn;
import com.enterprise.testagent.domain.run.RunResendSourceTurnQuery;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunResendTrigger;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 手动与定时自动重发共用的创建服务；此阶段绝不调用原生 revert。 */
@Service
public class RunResendApplicationService {

    static final Duration REPLAY_INPUT_TTL = Duration.ofHours(3);

    private final RunRepository runRepository;
    private final RunResendRepository resendRepository;
    private final RunResendReplayInputStore replayInputStore;
    private final RunResendSourceTurnQuery sourceTurnQuery;
    private final ConversationRunContextResolver contextResolver;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final RunEventAppender eventAppender;
    private final Clock clock;

    public RunResendApplicationService(
            RunRepository runRepository,
            RunResendRepository resendRepository,
            RunResendReplayInputStore replayInputStore,
            RunResendSourceTurnQuery sourceTurnQuery,
            ConversationRunContextResolver contextResolver,
            AgentRuntimeRegistry runtimeRegistry,
            RunEventAppender eventAppender,
            Clock clock) {
        this.runRepository = Objects.requireNonNull(runRepository);
        this.resendRepository = Objects.requireNonNull(resendRepository);
        this.replayInputStore = Objects.requireNonNull(replayInputStore);
        this.sourceTurnQuery = Objects.requireNonNull(sourceTurnQuery);
        this.contextResolver = Objects.requireNonNull(contextResolver);
        this.runtimeRegistry = Objects.requireNonNull(runtimeRegistry);
        this.eventAppender = Objects.requireNonNull(eventAppender);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 用户主动重发；幂等命中时直接返回同一个替代 Run。 */
    @Transactional
    public RunResend createManual(
            UserId owner,
            String agentId,
            SessionId sessionId,
            CreateRunResendCommand command,
            String traceId) {
        return createManualInternal(
                RunActorAttribution.direct(owner), null, agentId, sessionId, command, traceId);
    }

    /** 普通入口补齐真实发起人的统一认证号快照。 */
    @Transactional
    public RunResend createManual(
            RunActorAttribution attribution,
            String agentId,
            SessionId sessionId,
            CreateRunResendCommand command,
            String traceId) {
        return createManualInternal(attribution, null, agentId, sessionId, command, traceId);
    }

    /** 分享会话要求可对话；所属人或源消息的实际发送人可撤回重发。 */
    @Transactional
    public RunResend createManual(
            DelegatedOperationContext context,
            String agentId,
            SessionId sessionId,
            CreateRunResendCommand command,
            String traceId) {
        Objects.requireNonNull(context, "context must not be null");
        context.requireChat();
        context.requireSession(sessionId);
        return createManualInternal(
                RunActorAttribution.from(context), context, agentId, sessionId, command, traceId);
    }

    private RunResend createManualInternal(
            RunActorAttribution attribution,
            DelegatedOperationContext delegatedContext,
            String agentId,
            SessionId sessionId,
            CreateRunResendCommand command,
            String traceId) {
        Objects.requireNonNull(attribution, "attribution must not be null");
        UserId owner = attribution.executionOwnerUserId();
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(command, "command must not be null");
        RunResend existing = resendRepository
                .findByOwnerAndClientRequestId(owner, command.clientRequestId())
                .orElse(null);
        if (existing != null) {
            if (!existing.sessionId().equals(sessionId)) {
                throw conflict("clientRequestId 已用于其它会话", sessionId);
            }
            if (!existing.requesterUserId().equals(attribution.actualSenderUserId())) {
                throw conflict("clientRequestId 已被其他用户使用", sessionId);
            }
            return existing;
        }
        if (resendRepository.findActiveBySession(sessionId).isPresent()
                || runRepository.findLatestActiveBySessionId(sessionId).isPresent()) {
            throw conflict("会话仍在运行或已有重发等待执行", sessionId);
        }
        String resolvedAgentId = runtimeRegistry.normalize(agentId);
        StartRunInput contextInput = new StartRunInput(
                sessionId, "resend-context-validation", List.of(), null, null, null, null, null,
                null, null, command.contextToken(), command.clientRequestId());
        ConversationRunContext context = contextResolver
                .resolve(owner, resolvedAgentId, contextInput, traceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONVERSATION_CONTEXT_REQUIRED,
                        "撤销重发需要可信会话上下文"));
        if (context.bindingSnapshot() == null || context.remoteSessionId() == null) {
            throw conflict("会话缺少可恢复的远端边界", sessionId);
        }
        RunResendSourceTurn sourceTurn = sourceTurnQuery.findLatestUserTurn(sessionId).orElse(null);
        RunId sourceRunId;
        if (sourceTurn == null) {
            // REDIS_SUMMARY 不持久化用户正文；expectedRunId 只定位关系型锚点，远端读取仍会校验最后 user 边界。
            if (command.expectedRunId() == null) {
                throw conflict("会话没有可恢复的最后一条用户消息边界", sessionId);
            }
            sourceRunId = command.expectedRunId();
        } else {
            if (!sourceTurn.remoteMessageId().equals(command.expectedRemoteMessageId())
                    || (command.expectedRunId() != null && !sourceTurn.runId().equals(command.expectedRunId()))) {
                throw conflict("请求目标已不是最后一条用户消息，请刷新后重试", sessionId);
            }
            sourceRunId = sourceTurn.runId();
        }
        Run sourceRun = requireTerminalSourceRun(sourceRunId, sessionId);
        if (delegatedContext != null) {
            delegatedContext.requireWorkspace(sourceRun.workspaceId());
        }
        requireManualRequester(attribution, sourceRun, sessionId);
        AgentRuntime runtime = runtimeRegistry.require(resolvedAgentId);
        AgentReplayableTurn replayable;
        try {
            replayable = runtime.loadReplayableTurn(new AgentReplayableTurnCommand(
                            context.executionNodeSnapshot(),
                            context.remoteSessionId(),
                            context.trustedWorkspaceRoot(),
                            null,
                            command.expectedRemoteMessageId(),
                            traceId))
                    .block();
        } catch (IllegalStateException staleBoundary) {
            throw conflict("请求目标已不是远端最后一条用户消息，请刷新后重试", sessionId);
        }
        if (replayable == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "远端用户消息读取失败");
        }
        replayable = withEditedPrompt(replayable, command.editedPrompt());
        RunResend previous = resendRepository.findByReplacementRunId(sourceRun.runId()).orElse(null);
        return reserve(
                attribution,
                resolvedAgentId,
                sourceRun,
                command.expectedRemoteMessageId(),
                replayable,
                context.linuxServerId(),
                RunResendTrigger.MANUAL,
                previous == null ? 1 : previous.totalAttempt() + 1,
                previous == null ? 0 : previous.automaticAttempt(),
                clock.instant(),
                command.clientRequestId(),
                traceId);
    }

    private RunResend reserve(
            RunActorAttribution requester,
            String agentId,
            Run sourceRun,
            String sourceRemoteMessageId,
            AgentReplayableTurn replayable,
            String targetLinuxServerId,
            RunResendTrigger trigger,
            int totalAttempt,
            int automaticAttempt,
            Instant executeAt,
            String clientRequestId,
            String traceId) {
        UserId owner = requester.executionOwnerUserId();
        Instant now = clock.instant();
        RunId replacementRunId = new RunId(RuntimeIdGenerator.runId());
        String replacementMessageId = runtimeRegistry.require(agentId).createDispatchMessageId();
        Run replacement = new Run(
                replacementRunId,
                sourceRun.sessionId(),
                sourceRun.workspaceId(),
                RunStatus.PENDING,
                now,
                now,
                traceId)
                .withSource(sourceRun.sourceType(), sourceRun.sourceRefId(), owner)
                .withMessageSender(
                        sourceSender(sourceRun, owner),
                        sourceRun.messageSenderUnifiedAuthId(),
                        sourceRun.messageSentBySharedUser())
                .withRuntimeSelection(replayable.agent(), modelId(replayable));
        RunResend resend = new RunResend(
                new RunResendId(RuntimeIdGenerator.runResendId()),
                sourceRun.sessionId(),
                owner,
                sourceRun.runId(),
                replacementRunId,
                sourceRemoteMessageId,
                replacementMessageId,
                trigger,
                totalAttempt,
                automaticAttempt,
                RunResendPolicy.MAX_AUTOMATIC_ATTEMPTS,
                RunResendStatus.WAITING,
                executeAt,
                targetLinuxServerId,
                null,
                null,
                clientRequestId,
                traceId,
                null,
                now,
                now)
                .withRequester(
                        requester.actualSenderUserId(), requester.actualSenderUnifiedAuthId(),
                        requester.sentBySharedUser());

        // 精确输入先进入有限 TTL Redis；后续任何执行者在 revert 前都必须重新读取确认。
        replayInputStore.save(new RunResendReplayInput(
                replacementRunId,
                replayable.prompt(),
                replayable.parts().stream().map(this::partMap).toList(),
                replacementMessageId,
                replayable.agent(),
                replayable.modelProviderId(),
                replayable.modelId(),
                replayable.variant(),
                now,
                now.plus(REPLAY_INPUT_TTL)));
        runRepository.save(replacement);
        resendRepository.save(resend);
        if (!resendRepository.insertSessionLock(sourceRun.sessionId(), resend.resendId(), owner, now)) {
            throw conflict("会话已被其它重发请求锁定", sourceRun.sessionId());
        }
        eventAppender.append(new RunEventDraft(
                replacementRunId,
                RunEventType.RUN_RESEND_SCHEDULED,
                traceId,
                now,
                eventPayload(resend)), RunStorageMode.LEGACY_FULL);
        return resend;
    }

    /** 根 session.error 自动入口复用与手动入口完全相同的预留与输入保管流程。 */
    @Transactional
    RunResend reserveAutomatic(
            UserId owner,
            String agentId,
            Run sourceRun,
            String sourceRemoteMessageId,
            AgentReplayableTurn replayable,
            String targetLinuxServerId,
            int totalAttempt,
            int automaticAttempt,
            Instant executeAt,
            String clientRequestId,
            String traceId) {
        if (resendRepository.findActiveBySession(sourceRun.sessionId()).isPresent()) {
            throw conflict("会话已有重发等待执行", sourceRun.sessionId());
        }
        return reserve(
                RunActorAttribution.direct(owner),
                agentId,
                sourceRun,
                sourceRemoteMessageId,
                replayable,
                targetLinuxServerId,
                RunResendTrigger.AUTOMATIC,
                totalAttempt,
                automaticAttempt,
                executeAt,
                clientRequestId,
                traceId);
    }

    private void requireManualRequester(
            RunActorAttribution requester,
            Run sourceRun,
            SessionId sessionId) {
        UserId actor = requester.actualSenderUserId();
        UserId sourceSender = sourceRun.messageSenderUserId();
        if (!actor.equals(requester.executionOwnerUserId()) && !actor.equals(sourceSender)) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "只有会话所属人或最后一条消息的实际发送人可以撤回并重新发送",
                    Map.of("sessionId", sessionId.value()));
        }
    }

    /**
     * 用所属人确认后的新文本替换原轮次第一个可编辑文本，同时保留附件、Agent、模型和其它协议 part。
     * 原消息没有 text 时优先修改 subtask prompt；两者都没有时在首位补一个 text part。
     */
    private AgentReplayableTurn withEditedPrompt(AgentReplayableTurn replayable, String editedPrompt) {
        if (editedPrompt == null) {
            return replayable;
        }
        List<AgentPromptPart> parts = new ArrayList<>(replayable.parts());
        int editableIndex = -1;
        for (int index = 0; index < parts.size(); index++) {
            AgentPromptPart part = parts.get(index);
            if ("text".equals(part.type())) {
                editableIndex = index;
                break;
            }
        }
        if (editableIndex < 0) {
            for (int index = 0; index < parts.size(); index++) {
                if ("subtask".equals(parts.get(index).type())) {
                    editableIndex = index;
                    break;
                }
            }
        }
        if (editableIndex < 0) {
            parts.add(0, AgentPromptPart.text(editedPrompt));
        } else {
            AgentPromptPart original = parts.get(editableIndex);
            parts.set(editableIndex, new AgentPromptPart(
                    original.type(), editedPrompt, original.url(), original.mime(), original.filename(),
                    original.agentName(), original.source()));
        }
        return new AgentReplayableTurn(
                replayable.messageId(), editedPrompt, parts, replayable.agent(),
                replayable.modelProviderId(), replayable.modelId(), replayable.variant());
    }

    private UserId sourceSender(Run sourceRun, UserId owner) {
        if (sourceRun.messageSenderUserId() != null) return sourceRun.messageSenderUserId();
        return sourceRun.triggeredByUserId() == null ? owner : sourceRun.triggeredByUserId();
    }

    private Run requireTerminalSourceRun(RunId runId, SessionId sessionId) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "源 Run 不存在"));
        if (!run.sessionId().equals(sessionId) || !run.status().isTerminal()) {
            throw conflict("只有已终止 Run 的最后一条用户消息可以重发", sessionId);
        }
        if (run.sourceType() == ConversationSourceType.SIDE_QUESTION) {
            throw conflict("子 Agent 会话不支持撤销重发", sessionId);
        }
        return run;
    }

    private Map<String, Object> partMap(AgentPromptPart part) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("type", part.type());
        put(map, "text", part.text());
        put(map, "url", part.url());
        put(map, "mime", part.mime());
        put(map, "filename", part.filename());
        put(map, "agentName", part.agentName());
        if (!part.source().isEmpty()) map.put("source", part.source());
        return Map.copyOf(map);
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }

    private static String modelId(AgentReplayableTurn replayable) {
        return replayable.modelProviderId() == null
                ? replayable.modelId()
                : replayable.modelProviderId() + "/" + replayable.modelId();
    }

    static Map<String, Object> eventPayload(RunResend resend) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resendId", resend.resendId().value());
        payload.put("sourceRunId", resend.sourceRunId().value());
        payload.put("replacementRunId", resend.replacementRunId().value());
        payload.put("trigger", resend.trigger().name());
        payload.put("totalAttempt", resend.totalAttempt());
        payload.put("automaticAttempt", resend.automaticAttempt());
        payload.put("automaticLimit", resend.automaticLimit());
        payload.put("status", resend.status().name());
        payload.put("executeAt", resend.executeAt().toString());
        // OpenCode 仍以所属人执行；事件必须同时携带真实操作人，前端才能维持共享配色和停止权限。
        payload.put("requesterUserId", resend.requesterUserId().value());
        if (resend.requesterUnifiedAuthId() != null) {
            payload.put("requesterUnifiedAuthId", resend.requesterUnifiedAuthId());
        }
        payload.put("requestedBySharedUser", resend.requestedBySharedUser());
        return Map.copyOf(payload);
    }

    private PlatformException conflict(String message, SessionId sessionId) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                message,
                Map.of("sessionId", sessionId.value()));
    }
}
