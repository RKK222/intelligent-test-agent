package com.example.testagent.app.run;

import com.example.testagent.app.support.RuntimeIdGenerator;
import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventType;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeRepository;
import com.example.testagent.domain.routing.ExecutionNodeRouter;
import com.example.testagent.domain.routing.RoutingDecision;
import com.example.testagent.domain.routing.RoutingDecisionRepository;
import com.example.testagent.domain.routing.RoutingReason;
import com.example.testagent.domain.run.Run;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.run.RunRepository;
import com.example.testagent.domain.run.RunStatus;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionMessage;
import com.example.testagent.domain.session.SessionMessageId;
import com.example.testagent.domain.session.SessionMessageRepository;
import com.example.testagent.domain.session.SessionMessageRole;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceRepository;
import com.example.testagent.event.RunEventAppender;
import com.example.testagent.opencode.client.OpencodeCancelCommand;
import com.example.testagent.opencode.client.OpencodeClientFacade;
import com.example.testagent.opencode.client.OpencodeCreateSessionCommand;
import com.example.testagent.opencode.client.OpencodeCreateSessionResult;
import com.example.testagent.opencode.client.OpencodeStartRunCommand;
import com.example.testagent.opencode.client.OpencodeStreamEventsCommand;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Run 应用服务，集中编排持久化、路由、opencode 启动/取消和 RunEvent 追加。
 */
@Service
public class RunApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunApplicationService.class);
    private static final int ROUTING_CANDIDATE_LIMIT = 50;

    private final WorkspaceRepository workspaceRepository;
    private final com.example.testagent.domain.session.SessionRepository sessionRepository;
    private final RunRepository runRepository;
    private final SessionMessageRepository sessionMessageRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final RoutingDecisionRepository routingDecisionRepository;
    private final RunEventAppender runEventAppender;
    private final OpencodeClientFacade opencodeClientFacade;
    private final ExecutionNodeRouter executionNodeRouter = new ExecutionNodeRouter();

    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.example.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            OpencodeClientFacade opencodeClientFacade) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.sessionMessageRepository = Objects.requireNonNull(sessionMessageRepository, "sessionMessageRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.routingDecisionRepository = Objects.requireNonNull(routingDecisionRepository, "routingDecisionRepository must not be null");
        this.runEventAppender = Objects.requireNonNull(runEventAppender, "runEventAppender must not be null");
        this.opencodeClientFacade = Objects.requireNonNull(opencodeClientFacade, "opencodeClientFacade must not be null");
    }

    public Run startRun(SessionId sessionId, String prompt, String traceId) {
        Instant now = Instant.now();
        Session session = findSession(sessionId);
        Workspace workspace = findWorkspace(session.workspaceId());
        Run pending = new Run(
                new RunId(RuntimeIdGenerator.runId()),
                session.sessionId(),
                workspace.workspaceId(),
                RunStatus.PENDING,
                now,
                now,
                traceId);
        runRepository.save(pending);
        saveUserMessage(session.sessionId(), prompt, traceId, now);
        append(pending.runId(), RunEventType.RUN_CREATED, traceId, now, Map.of("status", RunStatus.PENDING.name()));

        try {
            OpencodeRoutingTarget target = resolveOpencodeTarget(session, pending.runId(), now, traceId);
            routingDecisionRepository.save(target.decision());
            Session opencodeSession = ensureOpencodeSession(session, workspace, target.node(), traceId);
            opencodeClientFacade.startRun(new OpencodeStartRunCommand(
                            target.node(),
                            opencodeSession.opencodeSessionId(),
                            workspace.rootPath(),
                            null,
                            prompt,
                            traceId))
                    .block();
            Run running = runRepository.save(pending.start(Instant.now()));
            append(running.runId(), RunEventType.RUN_STARTED, traceId, Instant.now(), Map.of("status", RunStatus.RUNNING.name()));
            subscribeOpencodeEvents(running, target.node(), workspace, traceId);
            return running;
        } catch (PlatformException exception) {
            Run failed = runRepository.save(pending.fail(Instant.now()));
            append(failed.runId(), RunEventType.RUN_FAILED, traceId, Instant.now(), Map.of("errorCode", exception.errorCode().name()));
            throw exception;
        }
    }

    public Run getRun(RunId runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Run 不存在", Map.of("runId", runId.value())));
    }

    public Run cancelRun(RunId runId, String traceId) {
        Run run = getRun(runId);
        if (run.status().isTerminal()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Run 已结束，不能取消",
                    Map.of("runId", runId.value(), "status", run.status().name()));
        }
        Run cancelling = run.status() == RunStatus.CANCELLING
                ? run
                : runRepository.save(run.requestCancel(Instant.now()));
        if (cancelling.status() == RunStatus.CANCELLING) {
            append(runId, RunEventType.RUN_CANCELLING, traceId, Instant.now(), Map.of("status", cancelling.status().name()));
        }
        RoutingDecision decision = routingDecisionRepository.findByRunId(runId).orElse(null);
        if (decision != null) {
            Session session = findSession(run.sessionId());
            Workspace workspace = findWorkspace(run.workspaceId());
            if (session.hasOpencodeSessionMapping()) {
                executionNodeRepository.findById(session.opencodeExecutionNodeId()).ifPresent(node ->
                        opencodeClientFacade.cancelSession(new OpencodeCancelCommand(
                                        node,
                                        session.opencodeSessionId(),
                                        workspace.rootPath(),
                                        null,
                                        traceId))
                                .block());
            }
        }
        Run cancelled = cancelling.status() == RunStatus.CANCELLED
                ? cancelling
                : runRepository.save(cancelling.cancel(Instant.now()));
        append(runId, RunEventType.RUN_CANCELLED, traceId, Instant.now(), Map.of("status", cancelled.status().name()));
        return cancelled;
    }

    private void saveUserMessage(SessionId sessionId, String prompt, String traceId, Instant createdAt) {
        sessionMessageRepository.save(new SessionMessage(
                new SessionMessageId(RuntimeIdGenerator.messageId()),
                sessionId,
                SessionMessageRole.USER,
                prompt,
                createdAt,
                traceId));
    }

    private Session findSession(SessionId sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在", Map.of("sessionId", sessionId.value())));
    }

    private Workspace findWorkspace(com.example.testagent.domain.workspace.WorkspaceId workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value())));
    }

    private OpencodeRoutingTarget resolveOpencodeTarget(Session session, RunId runId, Instant now, String traceId) {
        if (session.hasOpencodeSessionMapping()) {
            ExecutionNode node = executionNodeRepository.findById(session.opencodeExecutionNodeId())
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.OPENCODE_UNAVAILABLE,
                            "会话绑定的 opencode 执行节点不存在",
                            Map.of(
                                    "sessionId", session.sessionId().value(),
                                    "nodeId", session.opencodeExecutionNodeId().value())));
            if (!node.canAcceptRun()) {
                throw new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "会话绑定的 opencode 执行节点不可用",
                        Map.of(
                                "sessionId", session.sessionId().value(),
                                "nodeId", node.executionNodeId().value(),
                                "status", node.status().name()));
            }
            return new OpencodeRoutingTarget(
                    node,
                    new RoutingDecision(runId, node.executionNodeId(), RoutingReason.STICKY_SESSION, now, traceId));
        }

        RoutingDecision decision = executionNodeRouter.route(
                runId,
                executionNodeRepository.findRoutableNodes(ROUTING_CANDIDATE_LIMIT),
                now,
                traceId);
        ExecutionNode node = executionNodeRepository.findById(decision.executionNodeId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "路由节点不存在",
                        Map.of("nodeId", decision.executionNodeId().value())));
        return new OpencodeRoutingTarget(node, decision);
    }

    private Session ensureOpencodeSession(Session session, Workspace workspace, ExecutionNode node, String traceId) {
        if (session.hasOpencodeSessionMapping()) {
            return session;
        }
        // 首次 Run 才创建远端 opencode session，平台 ses_ ID 始终只留在平台内部。
        OpencodeCreateSessionResult created = opencodeClientFacade.createSession(new OpencodeCreateSessionCommand(
                        node,
                        workspace.rootPath(),
                        null,
                        session.title(),
                        traceId))
                .block();
        if (created == null) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "opencode 创建会话未返回结果",
                    Map.of("sessionId", session.sessionId().value(), "nodeId", node.executionNodeId().value()));
        }
        return sessionRepository.attachOpencodeSession(
                        session.sessionId(),
                        created.opencodeSessionId(),
                        node.executionNodeId(),
                        Instant.now(),
                        traceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Session 不存在",
                        Map.of("sessionId", session.sessionId().value())));
    }

    private void append(RunId runId, RunEventType type, String traceId, Instant occurredAt, Map<String, Object> payload) {
        runEventAppender.append(new RunEventDraft(runId, type, traceId, occurredAt, payload));
    }

    private void subscribeOpencodeEvents(Run run, ExecutionNode node, Workspace workspace, String traceId) {
        opencodeClientFacade.streamRunEvents(new OpencodeStreamEventsCommand(
                        node,
                        run.runId(),
                        workspace.rootPath(),
                        null,
                        traceId))
                .doOnNext(draft -> appendStreamEvent(run, draft))
                .doOnError(error -> failRunFromStream(run, traceId, error))
                .subscribe(ignored -> {
                }, ignored -> {
                    // 错误已在 doOnError 中落库，这里消费异常以避免 Reactor dropped error 日志。
                });
    }

    private void appendStreamEvent(Run originalRun, RunEventDraft draft) {
        if (draft.type() == RunEventType.RUN_SUCCEEDED || draft.type() == RunEventType.RUN_FAILED) {
            Run current = runRepository.findById(originalRun.runId()).orElse(originalRun);
            if (!current.status().isTerminal()) {
                if (draft.type() == RunEventType.RUN_SUCCEEDED) {
                    runRepository.save(current.succeed(draft.occurredAt()));
                } else {
                    runRepository.save(current.fail(draft.occurredAt()));
                }
                runEventAppender.append(draft);
            }
            return;
        }
        runEventAppender.append(draft);
    }

    private void failRunFromStream(Run run, String traceId, Throwable error) {
        try {
            Run current = runRepository.findById(run.runId()).orElse(run);
            if (!current.status().isTerminal()) {
                Run failed = runRepository.save(current.fail(Instant.now()));
                append(failed.runId(), RunEventType.RUN_FAILED, traceId, Instant.now(),
                        Map.of("error", error.getClass().getSimpleName()));
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to persist opencode stream failure, runId={}, traceId={}",
                    run.runId().value(), traceId, exception);
        }
    }

    private record OpencodeRoutingTarget(ExecutionNode node, RoutingDecision decision) {
    }
}
