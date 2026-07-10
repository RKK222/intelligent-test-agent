package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.event.RunEventScopeContext;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionTitleUpdateRepository;
import com.icbc.testagent.event.RunEventAppender;
import com.icbc.testagent.opencode.runtime.runtime.OpencodeRuntimeApplicationService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 首轮对话结束时的标题兜底：只复用 OpenCode 原生 title agent，不自定义模型或标题提示词。
 */
@Service
public class RunSessionTitleFallbackService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunSessionTitleFallbackService.class);
    private static final int EVENT_SCAN_LIMIT = 200;
    private final SessionRepository sessionRepository;
    private final SessionMessageRepository sessionMessageRepository;
    private final SessionTitleUpdateRepository sessionTitleUpdateRepository;
    private final RunEventRepository runEventRepository;
    private final RunEventAppender runEventAppender;
    private final RunEventPersistencePolicy runEventPersistencePolicy;
    private final OpencodeRuntimeApplicationService runtimeService;
    private final OpencodeSessionTitleProperties properties;
    private final ConcurrentMap<RunId, Boolean> pendingRuns = new ConcurrentHashMap<>();

    public RunSessionTitleFallbackService(
            SessionRepository sessionRepository,
            SessionMessageRepository sessionMessageRepository,
            SessionTitleUpdateRepository sessionTitleUpdateRepository,
            RunEventRepository runEventRepository,
            RunEventAppender runEventAppender,
            RunEventPersistencePolicy runEventPersistencePolicy,
            OpencodeRuntimeApplicationService runtimeService,
            OpencodeSessionTitleProperties properties) {
        this.sessionRepository = sessionRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.sessionTitleUpdateRepository = sessionTitleUpdateRepository;
        this.runEventRepository = runEventRepository;
        this.runEventAppender = runEventAppender;
        this.runEventPersistencePolicy = runEventPersistencePolicy;
        this.runtimeService = runtimeService;
        this.properties = properties;
    }

    /**
     * 异步执行兜底，不能占用 RunEvent 消费线程或改变已确认的 Run 成功状态。
     */
    public void schedule(String runtimeAgentId, Run run) {
        if (!eligible(runtimeAgentId, run) || pendingRuns.putIfAbsent(run.runId(), Boolean.TRUE) != null) {
            return;
        }
        Mono.fromRunnable(() -> fallbackIfNeeded(runtimeAgentId, run))
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(ignored -> pendingRuns.remove(run.runId()))
                .subscribe();
    }

    /**
     * 供异步入口和定向测试共用的单次策略执行。
     */
    void fallbackIfNeeded(String runtimeAgentId, Run run) {
        if (!eligible(runtimeAgentId, run) || hasSynchronizedTitle(run)) {
            return;
        }
        Optional<Session> session = sessionRepository.findById(run.sessionId());
        Optional<SessionMessage> firstUserMessage = sessionMessageRepository
                .findBySessionId(run.sessionId(), new PageRequest(1, EVENT_SCAN_LIMIT))
                .items()
                .stream()
                .filter(message -> message.role() == SessionMessageRole.USER)
                .findFirst();
        if (session.isEmpty()
                || firstUserMessage.isEmpty()
                || !run.runId().equals(firstUserMessage.get().runId())
                || !isInitialPlatformTitle(session.get(), firstUserMessage.get().content())) {
            return;
        }
        try {
            Optional<String> generatedTitle = runtimeService.withAgent(
                    "opencode",
                    run.triggeredByUserId(),
                    () -> runtimeService.generateNativeSessionTitle(
                            run.workspaceId().value(),
                            firstUserMessage.get().content(),
                            properties.getFallbackTimeout(),
                            properties.getFallbackPollInterval(),
                            run.traceId()));
            generatedTitle.filter(this::validTitle)
                    .ifPresent(title -> persistFallbackTitle(run, session.get(), firstUserMessage.get().content(), title));
        } catch (RuntimeException exception) {
            LOGGER.info(
                    "OpenCode 原生会话标题兜底未生成，保留临时标题，runId={}, traceId={}",
                    run.runId().value(),
                    run.traceId(),
                    exception);
        }
    }

    private void persistFallbackTitle(Run run, Session session, String prompt, String title) {
        String expectedTitle = OpencodeSessionTitlePolicy.initialPlatformTitle(prompt);
        if (!sessionTitleUpdateRepository.updateTitleIfCurrent(
                run.sessionId(), expectedTitle, title, Instant.now(), run.traceId())) {
            return;
        }
        RunEventScopeContext scope = RunEventScopeContext.root(run.runId(), session.opencodeSessionId());
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(scope.toPayloadMetadata());
        payload.put("rawType", "session.updated");
        payload.put("sessionID", session.opencodeSessionId());
        payload.put("info", java.util.Map.of("title", title));
        payload.put("platformSessionTitleSynchronized", true);
        payload.put("platformSessionTitle", title);
        payload.put("platformSessionTitleFallback", true);
        runEventAppender.append(runEventPersistencePolicy.sanitizeForPersistence(new RunEventDraft(
                run.runId(),
                RunEventType.SESSION_UPDATED,
                run.traceId(),
                Instant.now(),
                payload,
                scope)));
    }

    private boolean eligible(String runtimeAgentId, Run run) {
        return properties.isFallbackEnabled()
                && run.triggeredByUserId() != null
                && "opencode".equals(runtimeAgentId);
    }

    private boolean hasSynchronizedTitle(Run run) {
        return runEventRepository.findByRunIdAfter(run.runId(), 0, EVENT_SCAN_LIMIT).stream()
                .anyMatch(event -> Boolean.TRUE.equals(event.payload().get("platformSessionTitleSynchronized")));
    }

    private boolean validTitle(String title) {
        return title != null && !title.isBlank() && !OpencodeSessionTitlePolicy.isDefaultTitle(title);
    }

    private boolean isInitialPlatformTitle(Session session, String prompt) {
        return prompt != null && session.title().equals(OpencodeSessionTitlePolicy.initialPlatformTitle(prompt));
    }
}
