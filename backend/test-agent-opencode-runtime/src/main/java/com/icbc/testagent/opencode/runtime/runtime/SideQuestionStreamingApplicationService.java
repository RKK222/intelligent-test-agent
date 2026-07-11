package com.icbc.testagent.opencode.runtime.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.icbc.testagent.agent.runtime.AgentPromptPart;
import com.icbc.testagent.agent.runtime.AgentEventStream;
import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeCommand;
import com.icbc.testagent.agent.runtime.AgentRuntimeResult;
import com.icbc.testagent.agent.runtime.AgentSessionMessage;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesResult;
import com.icbc.testagent.agent.runtime.AgentStartRunCommand;
import com.icbc.testagent.agent.runtime.AgentStartRunResult;
import com.icbc.testagent.agent.runtime.AgentStreamEventsCommand;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.routing.RoutingDecision;
import com.icbc.testagent.domain.routing.RoutingDecisionRepository;
import com.icbc.testagent.domain.routing.RoutingReason;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.event.RunEventAppender;
import com.icbc.testagent.event.RunEventLiveBus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * 宠物旁路问答流式编排：创建独立归档 Session/Run 后立即返回，并在后台完成临时 fork、只读提问和清理。
 */
@Service
public class SideQuestionStreamingApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SideQuestionStreamingApplicationService.class);
    private static final String INTERNAL_SESSION_TITLE = "宠物旁路问答（内部）";
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
                .withRuntimeSelection(SideQuestionPolicy.PLAN_AGENT, normalizedModel);
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

    private void execute(
            Run pending,
            Session internalSession,
            SessionId mainSessionId,
            AgentRuntimeTargetResolver.SessionRuntimeTarget target,
            String question,
            String messageId,
            String model,
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
                    Map.of("sessionId", mainSessionId.value()));
            progress(running.runId(), "preparing_context", traceId, Map.of());

            AgentSessionMessagesResult context = target.runtime().sessionMessages(new AgentSessionMessagesCommand(
                            target.node(),
                            target.remoteSessionId(),
                            SideQuestionPolicy.CONTEXT_MESSAGE_LIMIT + 1,
                            "desc",
                            null,
                            traceId))
                    .block();
            boolean shouldCompact = SideQuestionPolicy.shouldCompact(context);

            progress(running.runId(), "forking", traceId, Map.of());
            temporarySessionId = fork(target, messageId, traceId);
            String createdTemporarySessionId = temporarySessionId;
            cleanup = new SideQuestionTemporarySessionCleanup(
                    () -> deleteTemporarySession(target, createdTemporarySessionId, traceId));
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

            ModelSelection selection = parseModel(model);
            boolean compacted = false;
            if (shouldCompact) {
                if (selection == null) {
                    throw new IllegalArgumentException("model is required when side-question context needs compaction");
                }
                progress(running.runId(), "compacting", traceId, Map.of());
                compact(target, temporarySessionId, selection, traceId);
                compacted = true;
            }

            RunEventDraft terminal = promptAndAwaitTerminal(
                    running,
                    target,
                    temporarySessionId,
                    question,
                    selection,
                    traceId);
            if (terminal.type() == RunEventType.RUN_FAILED) {
                throw new IllegalStateException("remote side-question run failed");
            }

            progress(running.runId(), "composing", traceId, Map.of());
            AgentSessionMessagesResult finalMessages = target.runtime().sessionMessages(new AgentSessionMessagesCommand(
                            target.node(),
                            temporarySessionId,
                            100,
                            "asc",
                            null,
                            traceId))
                    .block();
            String answer = extractFinalAnswer(finalMessages);
            if (answer == null) {
                throw new IllegalStateException("side-question final answer was empty");
            }
            TruncatedAnswer bounded = truncateAnswer(answer);
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("sideQuestion", true);
            payload.put("answer", bounded.answer());
            payload.put("compacted", compacted);
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
            String traceId) {
        SideQuestionEventProjector projector = new SideQuestionEventProjector(temporarySessionId);
        AtomicBoolean promptDispatched = new AtomicBoolean();
        AtomicBoolean promptedRunStarted = new AtomicBoolean();
        AgentEventStream opened = target.runtime().openRunEventStream(new AgentStreamEventsCommand(
                target.node(),
                run.runId(),
                temporarySessionId,
                target.directory(),
                null,
                traceId));
        Mono<RunEventDraft> remoteTerminal = opened.events()
                .filter(event -> belongsToTemporarySession(event, temporarySessionId))
                .filter(event -> afterPromptBoundary(event, promptDispatched, promptedRunStarted))
                .doOnNext(event -> publishProjected(run.runId(), projector.project(event)))
                .filter(event -> event.type() == RunEventType.RUN_SUCCEEDED || event.type() == RunEventType.RUN_FAILED)
                .next();
        Mono<RunEventDraft> deadline = Mono.delay(taskTimeout, timeoutScheduler)
                .flatMap(ignored -> Mono.error(new IllegalStateException("side-question task timed out")));
        Mono<RunEventDraft> terminal = Mono.firstWithSignal(remoteTerminal, deadline).cache();
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
                            null,
                            SideQuestionPolicy.PLAN_AGENT,
                            SideQuestionPolicy.SYSTEM_PROMPT,
                            selection == null ? null : selection.providerId(),
                            selection == null ? null : selection.modelId(),
                            null,
                            null,
                            null,
                            traceId))
                    .block();
            if (accepted == null || !accepted.accepted()) {
                throw new IllegalStateException("side-question prompt was not accepted");
            }
            RunEventDraft result = terminal.block();
            if (result == null) {
                throw new IllegalStateException("side-question event stream ended without terminal event");
            }
            return result;
        } finally {
            eagerSubscription.dispose();
        }
    }

    /** fork 建连时可能先收到旧 idle；只有本次 prompt 已发出且观察到 RUN_STARTED 后才放行事件。 */
    private boolean afterPromptBoundary(
            RunEventDraft event,
            AtomicBoolean promptDispatched,
            AtomicBoolean promptedRunStarted) {
        if (promptedRunStarted.get()) {
            return true;
        }
        return promptDispatched.get()
                && opensPromptBoundary(event)
                && promptedRunStarted.compareAndSet(false, true);
    }

    /**
     * 实验事件系统用 RUN_STARTED；默认 legacy 事件系统以当前 temp 的原始 status.type=busy 表示本次 prompt 开始。
     */
    private boolean opensPromptBoundary(RunEventDraft event) {
        if (event.type() == RunEventType.RUN_STARTED) {
            return true;
        }
        return event.type() == RunEventType.SESSION_STATUS
                && containsRawBusyStatus(event.payload().get("rawPayload"));
    }

    /** 只检查 rawPayload 内真实 status 对象，不信任 mapper 补充的顶层 scope/status 别名。 */
    private boolean containsRawBusyStatus(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object statusValue = map.get("status");
            if (statusValue instanceof Map<?, ?> status
                    && status.get("type") instanceof String type
                    && "busy".equals(type.trim())) {
                return true;
            }
            for (Object nested : map.values()) {
                if (containsRawBusyStatus(nested)) {
                    return true;
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                if (containsRawBusyStatus(nested)) {
                    return true;
                }
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

    private void compact(
            AgentRuntimeTargetResolver.SessionRuntimeTarget target,
            String temporarySessionId,
            ModelSelection selection,
            String traceId) {
        runtimeCall(
                target,
                "POST",
                "/session/" + temporarySessionId + "/summarize",
                Map.of("providerID", selection.providerId(), "modelID", selection.modelId()),
                traceId);
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

    private String extractFinalAnswer(AgentSessionMessagesResult result) {
        if (result == null) {
            return null;
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (AgentSessionMessage message : result.messages()) {
            messages.add(Map.of("info", message.message(), "parts", message.parts()));
        }
        return answerExtractor.extract(messages);
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

    private record ModelSelection(String providerId, String modelId) {
    }

    private record TruncatedAnswer(String answer, boolean truncated) {
    }
}
