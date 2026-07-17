package com.enterprise.testagent.opencode.runtime.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.enterprise.testagent.agent.runtime.AgentPromptPart;
import com.enterprise.testagent.agent.runtime.AgentEventStream;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.agent.runtime.AgentSessionMessage;
import com.enterprise.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.enterprise.testagent.agent.runtime.AgentSessionMessagesResult;
import com.enterprise.testagent.agent.runtime.AgentStartRunCommand;
import com.enterprise.testagent.agent.runtime.AgentStartRunResult;
import com.enterprise.testagent.agent.runtime.AgentStreamEventsCommand;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.routing.RoutingDecision;
import com.enterprise.testagent.domain.routing.RoutingDecisionRepository;
import com.enterprise.testagent.domain.routing.RoutingReason;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.event.RunEventLiveBus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 宠物旁路问答流式编排：创建独立归档 Session/Run 后立即返回，并在后台完成临时 fork、上下文提问和清理。
 */
@Service
public class SideQuestionStreamingApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SideQuestionStreamingApplicationService.class);
    private static final String INTERNAL_SESSION_TITLE = "宠物旁路问答（内部）";
    private static final String MANUAL_SESSION_TITLE = "手册问答（内部）";
    private static final String SAFE_FAILURE_MESSAGE = "旁路问答暂时失败";

    private final SessionRepository sessionRepository;
    private final RunRepository runRepository;
    private final RoutingDecisionRepository routingDecisionRepository;
    private final RunEventAppender runEventAppender;
    private final RunEventLiveBus runEventLiveBus;
    private final AgentRuntimeTargetResolver targetResolver;
    private final SideQuestionTerminalService terminalService;
    private final Scheduler backgroundScheduler;
    private final Duration taskTimeout;
    private final Scheduler timeoutScheduler;
    private final SideQuestionAnswerExtractor answerExtractor = new SideQuestionAnswerExtractor();

    /** 生产环境使用 bounded-elastic 执行阻塞式仓储和远端调用，避免占用 WebFlux 事件线程。 */
    @Autowired
    public SideQuestionStreamingApplicationService(
            SessionRepository sessionRepository,
            RunRepository runRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            RunEventLiveBus runEventLiveBus,
            AgentRuntimeTargetResolver targetResolver,
            SideQuestionTerminalService terminalService) {
        this(
                sessionRepository,
                runRepository,
                routingDecisionRepository,
                runEventAppender,
                runEventLiveBus,
                targetResolver,
                terminalService,
                Schedulers.boundedElastic(),
                SideQuestionPolicy.TASK_TIMEOUT,
                Schedulers.parallel());
    }

    /** 测试可注入确定性 Scheduler；生产调用方使用上方构造器。 */
    SideQuestionStreamingApplicationService(
            SessionRepository sessionRepository,
            RunRepository runRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            RunEventLiveBus runEventLiveBus,
            AgentRuntimeTargetResolver targetResolver,
            SideQuestionTerminalService terminalService,
            Scheduler backgroundScheduler) {
        this(
                sessionRepository,
                runRepository,
                routingDecisionRepository,
                runEventAppender,
                runEventLiveBus,
                targetResolver,
                terminalService,
                backgroundScheduler,
                SideQuestionPolicy.TASK_TIMEOUT,
                Schedulers.parallel());
    }

    /** 测试可独立注入绝对任务时限及其时钟，避免依赖真实等待。 */
    SideQuestionStreamingApplicationService(
            SessionRepository sessionRepository,
            RunRepository runRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            RunEventLiveBus runEventLiveBus,
            AgentRuntimeTargetResolver targetResolver,
            SideQuestionTerminalService terminalService,
            Scheduler backgroundScheduler,
            Duration taskTimeout,
            Scheduler timeoutScheduler) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.routingDecisionRepository = Objects.requireNonNull(routingDecisionRepository, "routingDecisionRepository must not be null");
        this.runEventAppender = Objects.requireNonNull(runEventAppender, "runEventAppender must not be null");
        this.runEventLiveBus = Objects.requireNonNull(runEventLiveBus, "runEventLiveBus must not be null");
        this.targetResolver = Objects.requireNonNull(targetResolver, "targetResolver must not be null");
        this.terminalService = Objects.requireNonNull(terminalService, "terminalService must not be null");
        this.backgroundScheduler = Objects.requireNonNull(backgroundScheduler, "backgroundScheduler must not be null");
        this.taskTimeout = Objects.requireNonNull(taskTimeout, "taskTimeout must not be null");
        if (taskTimeout.isZero() || taskTimeout.isNegative()) {
            throw new IllegalArgumentException("taskTimeout must be positive");
        }
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler must not be null");
    }

    /**
     * 创建旁路内部 Session 和 PENDING Run 并立即返回；问题正文不会写入任一平台 Session 消息表。
     */
    public SideQuestionRunStartResult start(
            UserId userId,
            String agentId,
            SessionId mainSessionId,
            String question,
            String messageId,
            String model,
            String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(mainSessionId, "mainSessionId must not be null");
        String normalizedQuestion = SideQuestionPolicy.requireQuestion(question);
        String normalizedMessageId = normalizeOptional(messageId);
        String normalizedModel = normalizeOptional(model);
        Session mainSession = sessionRepository.findById(mainSessionId)
                .orElseThrow(() -> new IllegalArgumentException("main session does not exist"));
        AgentRuntimeTargetResolver.SessionRuntimeTarget target = targetResolver.sessionTarget(
                agentId,
                userId,
                mainSessionId.value(),
                traceId);

        Instant now = Instant.now();
        Session internalSession = new Session(
                        new SessionId(RuntimeIdGenerator.sessionId()),
                        mainSession.workspaceId(),
                        INTERNAL_SESSION_TITLE,
                        SessionStatus.ARCHIVED,
                        now,
                        now,
                        traceId)
                .withSource(ConversationSourceType.SIDE_QUESTION, mainSessionId.value(), userId);
        internalSession = sessionRepository.save(internalSession);

        Run pending = new Run(
                        new RunId(RuntimeIdGenerator.runId()),
                        internalSession.sessionId(),
                        mainSession.workspaceId(),
                        RunStatus.PENDING,
                        now,
                        now,
                        traceId)
                .withSource(ConversationSourceType.SIDE_QUESTION, mainSessionId.value(), userId)
                .withRuntimeSelection(null, normalizedModel);
        pending = runRepository.save(pending);
        routingDecisionRepository.save(new RoutingDecision(
                pending.runId(),
                target.node().executionNodeId(),
                RoutingReason.MANUAL_OVERRIDE,
                now,
                traceId));
        appendDurable(pending.runId(), RunEventType.RUN_CREATED, traceId, now, Map.of("status", RunStatus.PENDING.name()));

        Run taskRun = pending;
        Session taskSession = internalSession;
        Mono.fromRunnable(() -> execute(
                        taskRun,
                        taskSession,
                        mainSessionId,
                        target,
                        normalizedQuestion,
                        normalizedMessageId,
                        normalizedModel,
                        true,
                        traceId))
                .subscribeOn(backgroundScheduler)
                .subscribe(
                        ignored -> {
                        },
                        error -> LOGGER.error(
                                "event=side_question_background_unhandled runId={} error={} traceId={}",
                                taskRun.runId().value(),
                                error.getClass().getSimpleName(),
                                traceId));
        return new SideQuestionRunStartResult(pending.runId());
    }

    /**
     * 无主对话时为手册问答创建独立归档 Session/Run。
     *
     * <p>公共目标解析器会为该内部 Session 创建远端会话；后台回答完成后删除远端会话，
     * 问题和答案均不进入普通会话列表或消息历史。
     */
    public SideQuestionRunStartResult startManual(
            UserId userId,
            String agentId,
            WorkspaceId workspaceId,
            String question,
            String model,
            String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        String normalizedQuestion = SideQuestionPolicy.requireQuestion(question);
        String normalizedModel = normalizeOptional(model);

        Instant now = Instant.now();
        Session internalSession = new Session(
                        new SessionId(RuntimeIdGenerator.sessionId()),
                        workspaceId,
                        MANUAL_SESSION_TITLE,
                        SessionStatus.ARCHIVED,
                        now,
                        now,
                        traceId)
                .withSource(ConversationSourceType.SIDE_QUESTION, null, userId);
        internalSession = sessionRepository.save(internalSession);

        // 复用公共 session 目标解析，统一完成用户进程节点选择、远端会话创建和映射持久化。
        AgentRuntimeTargetResolver.SessionRuntimeTarget target = targetResolver.sessionTarget(
                agentId,
                userId,
                internalSession.sessionId().value(),
                traceId);
        Run pending = new Run(
                        new RunId(RuntimeIdGenerator.runId()),
                        internalSession.sessionId(),
                        workspaceId,
                        RunStatus.PENDING,
                        now,
                        now,
                        traceId)
                .withSource(ConversationSourceType.SIDE_QUESTION, null, userId)
                .withRuntimeSelection(null, normalizedModel);
        pending = runRepository.save(pending);
        routingDecisionRepository.save(new RoutingDecision(
                pending.runId(),
                target.node().executionNodeId(),
                RoutingReason.MANUAL_OVERRIDE,
                now,
                traceId));
        appendDurable(pending.runId(), RunEventType.RUN_CREATED, traceId, now, Map.of("status", RunStatus.PENDING.name()));

        Run taskRun = pending;
        Session taskSession = internalSession;
        Mono.fromRunnable(() -> execute(
                        taskRun,
                        taskSession,
                        null,
                        target,
                        normalizedQuestion,
                        null,
                        normalizedModel,
                        false,
                        traceId))
                .subscribeOn(backgroundScheduler)
                .subscribe(
                        ignored -> {
                        },
                        error -> LOGGER.error(
                                "event=manual_question_background_unhandled runId={} error={} traceId={}",
                                taskRun.runId().value(),
                                error.getClass().getSimpleName(),
                                traceId));
        return new SideQuestionRunStartResult(pending.runId());
    }

    private void execute(
            Run pending,
            Session internalSession,
            SessionId sourceSessionId,
            AgentRuntimeTargetResolver.SessionRuntimeTarget target,
            String question,
            String messageId,
            String model,
            boolean forkSourceSession,
            String traceId) {
        SideQuestionTemporarySessionCleanup cleanup = null;
        String temporarySessionId = null;
        try {
            Run candidate = pending.start(Instant.now());
            Run running = runRepository.saveIfStatus(candidate, RunStatus.PENDING);
            if (running != candidate) {
                return;
            }
            appendDurable(running.runId(), RunEventType.RUN_STARTED, traceId, Instant.now(), Map.of("status", RunStatus.RUNNING.name()));
            appendDurable(
                    running.runId(),
                    RunEventType.SIDE_QUESTION_STARTED,
                    traceId,
                    Instant.now(),
                    sourceSessionId == null
                            ? Map.of("knowledgeBase", "user-manual")
                            : Map.of("sessionId", sourceSessionId.value()));
            progress(running.runId(), forkSourceSession ? "forking" : "preparing_context", traceId, Map.of());
            temporarySessionId = forkSourceSession
                    ? fork(target, messageId, traceId)
                    : target.remoteSessionId();
            String createdTemporarySessionId = temporarySessionId;
            cleanup = new SideQuestionTemporarySessionCleanup(
                    () -> deleteTemporarySession(target, createdTemporarySessionId, traceId));
            if (forkSourceSession) {
                LOGGER.info(
                        "event=side_question_fork_created_pending_mapping runId={} remoteSessionId={} nodeId={} traceId={}",
                        running.runId().value(),
                        temporarySessionId,
                        target.node().executionNodeId().value(),
                        traceId);
                String mappedTemporarySessionId = temporarySessionId;
                sessionRepository.attachOpencodeSession(
                                internalSession.sessionId(),
                                mappedTemporarySessionId,
                                target.node().executionNodeId(),
                                Instant.now(),
                                traceId)
                        .orElseThrow(() -> new IllegalStateException("side-question fork mapping could not be saved"));
                LOGGER.info(
                        "event=side_question_fork_mapping_saved runId={} remoteSessionId={} nodeId={} traceId={}",
                        running.runId().value(),
                        temporarySessionId,
                        target.node().executionNodeId().value(),
                        traceId);
            }

            String promptMessageId = RuntimeIdGenerator.messageId();
            Set<String> baselineMessageIds = snapshotMessageIds(target, temporarySessionId, traceId);
            RunEventDraft terminal = promptAndAwaitTerminal(
                    running,
                    target,
                    temporarySessionId,
                    question,
                    parseModel(model),
                    promptMessageId,
                    baselineMessageIds,
                    traceId);
            if (terminal.type() == RunEventType.RUN_FAILED) {
                throw new IllegalStateException("remote side-question run failed");
            }

            progress(running.runId(), "composing", traceId, Map.of());
            AgentSessionMessagesResult finalMessages = target.runtime().sessionMessages(new AgentSessionMessagesCommand(
                            target.node(),
                            temporarySessionId,
                            100,
                            "desc",
                            null,
                            traceId))
                    .block();
            String answer = extractFinalAnswer(finalMessages, baselineMessageIds, true);
            if (answer == null) {
                throw new IllegalStateException("side-question final answer was empty");
            }
            TruncatedAnswer bounded = truncateAnswer(answer);
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("sideQuestion", true);
            payload.put("answer", bounded.answer());
            payload.put("compacted", false);
            if (bounded.truncated()) {
                payload.put("truncated", true);
            }
            terminalService.succeed(running.runId(), Map.copyOf(payload), traceId);
            // 成功路径主动清理；finally 会再次进入同一 guard，验证重复调用不会发出第二次 DELETE。
            cleanup.run();
        } catch (RuntimeException error) {
            LOGGER.warn(
                    "event=side_question_execution_failed runId={} error={} traceId={}",
                    pending.runId().value(),
                    error.getClass().getSimpleName(),
                    traceId);
            terminalService.fail(pending.runId(), SAFE_FAILURE_MESSAGE, traceId);
        } finally {
            if (cleanup != null) {
                cleanup.run();
            }
        }
    }

    private RunEventDraft promptAndAwaitTerminal(
            Run run,
            AgentRuntimeTargetResolver.SessionRuntimeTarget target,
            String temporarySessionId,
            String question,
            ModelSelection selection,
            String promptMessageId,
            Set<String> baselineMessageIds,
            String traceId) {
        SideQuestionEventProjector projector = new SideQuestionEventProjector(temporarySessionId, baselineMessageIds);
        AtomicBoolean promptDispatched = new AtomicBoolean();
        AtomicBoolean promptAccepted = new AtomicBoolean();
        AgentEventStream opened = target.runtime().openRunEventStream(new AgentStreamEventsCommand(
                target.node(),
                run.runId(),
                temporarySessionId,
                target.directory(),
                null,
                traceId));
        Mono<RunEventDraft> remoteTerminal = opened.events()
                .filter(event -> belongsToTemporarySession(event, temporarySessionId))
                .filter(event -> promptDispatched.get())
                .doOnNext(event -> publishProjected(run.runId(), projector.project(event)))
                .handle((event, sink) -> {
                    if (event.type() == RunEventType.RUN_FAILED || event.type() == RunEventType.SESSION_ERROR) {
                        sink.next(remoteTerminal(run, event, RunEventType.RUN_FAILED));
                    } else if (projector.answerCompleted()
                            || (event.type() == RunEventType.RUN_SUCCEEDED && projector.hasObservedAnswerMessage())) {
                        sink.next(remoteTerminal(run, event, RunEventType.RUN_SUCCEEDED));
                    }
                })
                .cast(RunEventDraft.class)
                .next()
                .onErrorResume(error -> {
                    LOGGER.warn(
                            "event=side_question_event_stream_failed runId={} error={} traceId={}",
                            run.runId().value(),
                            error.getClass().getSimpleName(),
                            traceId);
                    return Mono.empty();
                });
        Mono<RunEventDraft> recoveredTerminal = Flux.interval(
                        SideQuestionPolicy.MESSAGE_RECOVERY_INTERVAL,
                        SideQuestionPolicy.MESSAGE_RECOVERY_INTERVAL,
                        timeoutScheduler)
                .filter(ignored -> promptAccepted.get())
                .concatMap(ignored -> completedAnswerSnapshot(target, temporarySessionId, baselineMessageIds, traceId)
                        .onErrorResume(error -> {
                            LOGGER.debug(
                                    "event=side_question_message_recovery_failed runId={} error={} traceId={}",
                                    run.runId().value(),
                                    error.getClass().getSimpleName(),
                                    traceId);
                            return Mono.empty();
                        }))
                .filter(Boolean.TRUE::equals)
                .map(ignored -> new RunEventDraft(
                        run.runId(),
                        RunEventType.RUN_SUCCEEDED,
                        traceId,
                        Instant.now(),
                        Map.of("recoveredFrom", "session_messages")))
                .next();
        Mono<RunEventDraft> deadline = Mono.delay(taskTimeout, timeoutScheduler)
                .flatMap(ignored -> Mono.error(new IllegalStateException("side-question task timed out")));
        Mono<RunEventDraft> terminal = Mono.firstWithSignal(
                        Mono.firstWithValue(remoteTerminal, recoveredTerminal),
                        deadline)
                .cache();
        // 必须先建立订阅，再发送 prompt_async，避免极快终态在订阅前丢失。
        Disposable eagerSubscription = terminal.subscribe(ignored -> {
        }, ignored -> {
        });
        try {
            // ready 由 WebClient 收到远端 SSE 响应头后完成，不使用 doOnSubscribe/doFirst 伪造握手。
            opened.ready().block();
            progress(run.runId(), "reading", traceId, Map.of());
            promptDispatched.set(true);
            AgentStartRunResult accepted = target.runtime().startRun(new AgentStartRunCommand(
                            target.node(),
                            temporarySessionId,
                            target.directory(),
                            null,
                            question,
                            List.of(AgentPromptPart.text(question)),
                            promptMessageId,
                            null,
                            null,
                            selection == null ? null : selection.providerId(),
                            selection == null ? null : selection.modelId(),
                            null,
                            Map.of("*", false),
                            null,
                            null,
                            traceId))
                    .block();
            if (accepted == null || !accepted.accepted()) {
                throw new IllegalStateException("side-question prompt was not accepted");
            }
            promptAccepted.set(true);
            RunEventDraft result = terminal.block();
            if (result == null) {
                throw new IllegalStateException("side-question event stream ended without terminal event");
            }
            return result;
        } finally {
            eagerSubscription.dispose();
        }
    }

    /** 把远端失败/成功信号换成当前旁路 Run，避免临时流携带占位 runId。 */
    private RunEventDraft remoteTerminal(Run run, RunEventDraft source, RunEventType type) {
        return new RunEventDraft(
                run.runId(),
                type,
                source.traceId(),
                source.occurredAt(),
                source.payload(),
                source.scopeContext());
    }

    /**
     * SSE 丢失 idle/finish 时按低频消息快照恢复终态；只有本次 prompt 的 assistant 已完成且有正文才放行。
     */
    private Mono<Boolean> completedAnswerSnapshot(
            AgentRuntimeTargetResolver.SessionRuntimeTarget target,
            String temporarySessionId,
            Set<String> baselineMessageIds,
            String traceId) {
        return target.runtime().sessionMessages(new AgentSessionMessagesCommand(
                        target.node(),
                        temporarySessionId,
                        100,
                        "desc",
                        null,
                        traceId))
                .map(messages -> hasCompletedAnswer(messages, baselineMessageIds));
    }

    private boolean hasCompletedAnswer(AgentSessionMessagesResult result, Set<String> baselineMessageIds) {
        if (result == null) {
            return false;
        }
        for (AgentSessionMessage message : result.messages()) {
            Map<String, Object> info = message.message();
            if (!"assistant".equals(text(info.get("role")))) {
                continue;
            }
            String messageId = firstText(info, "messageID", "messageId", "id");
            if (messageId == null || baselineMessageIds.contains(messageId)) {
                continue;
            }
            if (firstText(info, "finish") != null
                    && extractFinalAnswer(
                                    new AgentSessionMessagesResult(List.of(message)),
                                    baselineMessageIds,
                                    false)
                            != null) {
                return true;
            }
        }
        return false;
    }

    private void publishProjected(RunId runId, List<RunEventDraft> projectedEvents) {
        for (RunEventDraft projected : projectedEvents) {
            RunEventDraft event = new RunEventDraft(
                    runId,
                    projected.type(),
                    projected.traceId(),
                    projected.occurredAt(),
                    projected.payload(),
                    projected.scopeContext());
            if (event.type() == RunEventType.SIDE_QUESTION_DELTA) {
                runEventLiveBus.publishTransient(event);
            } else {
                runEventAppender.append(event);
            }
        }
    }

    private String fork(
            AgentRuntimeTargetResolver.SessionRuntimeTarget target,
            String messageId,
            String traceId) {
        Map<String, Object> body = messageId == null ? Map.of() : Map.of("messageID", messageId);
        AgentRuntimeResult result = runtimeCall(
                target,
                "POST",
                "/session/" + target.remoteSessionId() + "/fork",
                body,
                traceId);
        String sessionId = extractSessionId(result == null ? null : result.body());
        if (sessionId == null) {
            throw new IllegalStateException("side-question fork did not return a session id");
        }
        return sessionId;
    }

    private AgentRuntimeResult runtimeCall(
            AgentRuntimeTargetResolver.SessionRuntimeTarget target,
            String method,
            String path,
            Map<String, Object> body,
            String traceId) {
        return target.runtime().runtime(new AgentRuntimeCommand(
                        target.node(),
                        method,
                        path,
                        target.directory(),
                        null,
                        Map.of(),
                        body,
                        traceId))
                .block();
    }

    private void deleteTemporarySession(
            AgentRuntimeTargetResolver.SessionRuntimeTarget target,
            String temporarySessionId,
            String traceId) {
        try {
            runtimeCall(target, "DELETE", "/session/" + temporarySessionId, Map.of(), traceId);
        } catch (RuntimeException cleanupFailure) {
            LOGGER.warn(
                    "event=side_question_cleanup_failed remoteSessionId={} error={} traceId={}",
                    temporarySessionId,
                    cleanupFailure.getClass().getSimpleName(),
                    traceId);
        }
    }

    private void appendDurable(
            RunId runId,
            RunEventType type,
            String traceId,
            Instant occurredAt,
            Map<String, Object> payload) {
        runEventAppender.append(new RunEventDraft(runId, type, traceId, occurredAt, payload));
    }

    private void progress(RunId runId, String stage, String traceId, Map<String, Object> details) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", stage);
        if (details != null) {
            payload.putAll(details);
        }
        appendDurable(runId, RunEventType.SIDE_QUESTION_PROGRESS, traceId, Instant.now(), Map.copyOf(payload));
    }

    private String extractFinalAnswer(
            AgentSessionMessagesResult result,
            Set<String> baselineMessageIds,
            boolean newestFirst) {
        if (result == null) {
            return null;
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (AgentSessionMessage message : result.messages()) {
            String messageId = firstText(message.message(), "messageID", "messageId", "id");
            if (messageId == null || baselineMessageIds.contains(messageId)) {
                continue;
            }
            messages.add(Map.of("info", message.message(), "parts", message.parts()));
        }
        // sessionMessages(desc) 返回新消息在前；提取器按时间正序取最后一条 assistant，故先反转。
        if (newestFirst) {
            Collections.reverse(messages);
        }
        return answerExtractor.extract(messages);
    }

    /** 记录 fork 继承的消息边界，后续只接收新 message ID，避免依赖 OpenCode 的非标准 parentID。 */
    private Set<String> snapshotMessageIds(
            AgentRuntimeTargetResolver.SessionRuntimeTarget target,
            String temporarySessionId,
            String traceId) {
        AgentSessionMessagesResult result = target.runtime().sessionMessages(new AgentSessionMessagesCommand(
                        target.node(),
                        temporarySessionId,
                        100,
                        "desc",
                        null,
                        traceId))
                .block();
        if (result == null) {
            return Set.of();
        }
        Set<String> messageIds = new HashSet<>();
        for (AgentSessionMessage message : result.messages()) {
            String messageId = firstText(message.message(), "messageID", "messageId", "id");
            if (messageId != null) {
                messageIds.add(messageId);
            }
        }
        return Set.copyOf(messageIds);
    }

    private TruncatedAnswer truncateAnswer(String answer) {
        String validUnicode = sanitizeUnicodeScalars(answer);
        if (validUnicode.getBytes(StandardCharsets.UTF_8).length <= SideQuestionPolicy.MAX_ANSWER_BYTES) {
            return new TruncatedAnswer(validUnicode, false);
        }
        StringBuilder builder = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < validUnicode.length(); ) {
            int codePoint = validUnicode.codePointAt(offset);
            int codePointBytes = utf8Length(codePoint);
            if (bytes + codePointBytes > SideQuestionPolicy.MAX_ANSWER_BYTES) {
                break;
            }
            builder.appendCodePoint(codePoint);
            bytes += codePointBytes;
            offset += Character.charCount(codePoint);
        }
        return new TruncatedAnswer(builder.toString(), true);
    }

    /** 把孤立 surrogate 替换为 Unicode replacement character，避免终态 JSON 携带非法标量。 */
    private String sanitizeUnicodeScalars(String value) {
        StringBuilder sanitized = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); ) {
            char current = value.charAt(offset);
            if (Character.isHighSurrogate(current)
                    && offset + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(offset + 1))) {
                sanitized.append(current).append(value.charAt(offset + 1));
                offset += 2;
                continue;
            }
            if (Character.isSurrogate(current)) {
                sanitized.append('\uFFFD');
            } else {
                sanitized.append(current);
            }
            offset++;
        }
        return sanitized.toString();
    }

    private int utf8Length(int codePoint) {
        if (codePoint <= 0x7f) {
            return 1;
        }
        if (codePoint <= 0x7ff) {
            return 2;
        }
        if (codePoint <= 0xffff) {
            return 3;
        }
        return 4;
    }

    private boolean belongsToTemporarySession(RunEventDraft event, String temporarySessionId) {
        Object rawPayload = event.payload().get("rawPayload");
        java.util.HashSet<String> sessionIds = new java.util.HashSet<>();
        collectSessionIds(rawPayload, sessionIds);
        return !sessionIds.isEmpty() && sessionIds.stream().allMatch(temporarySessionId::equals);
    }

    private void collectSessionIds(Object value, java.util.Set<String> sessionIds) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                if (("sessionID".equals(String.valueOf(key)) || "sessionId".equals(String.valueOf(key)))
                        && nested instanceof String id
                        && !id.isBlank()) {
                    sessionIds.add(id.trim());
                } else {
                    collectSessionIds(nested, sessionIds);
                }
            });
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(nested -> collectSessionIds(nested, sessionIds));
        }
    }

    private String extractSessionId(JsonNode node) {
        if (node == null) {
            return null;
        }
        for (String key : List.of("sessionID", "sessionId", "id")) {
            JsonNode candidate = node.get(key);
            if (candidate != null && candidate.isTextual() && !candidate.textValue().isBlank()) {
                return candidate.textValue().trim();
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                String candidate = extractSessionId(child);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private ModelSelection parseModel(String model) {
        if (model == null) {
            return null;
        }
        int separator = model.indexOf('/');
        if (separator <= 0 || separator == model.length() - 1) {
            return null;
        }
        return new ModelSelection(model.substring(0, separator), model.substring(separator + 1));
    }

    private String normalizeOptional(String value) {
        return Optional.ofNullable(value).map(String::trim).filter(text -> !text.isEmpty()).orElse(null);
    }

    private String firstText(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String candidate = text(values.get(key));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private String text(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record ModelSelection(String providerId, String modelId) {
    }

    private record TruncatedAnswer(String answer, boolean truncated) {
    }
}
