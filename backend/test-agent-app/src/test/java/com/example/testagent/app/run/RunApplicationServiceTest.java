package com.example.testagent.app.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventId;
import com.example.testagent.domain.event.RunEventRepository;
import com.example.testagent.domain.event.RunEventType;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeRepository;
import com.example.testagent.domain.node.ExecutionNodeStatus;
import com.example.testagent.domain.routing.RoutingDecision;
import com.example.testagent.domain.routing.RoutingDecisionRepository;
import com.example.testagent.domain.run.Run;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.run.RunRepository;
import com.example.testagent.domain.run.RunStatus;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionMessage;
import com.example.testagent.domain.session.SessionMessageId;
import com.example.testagent.domain.session.SessionMessageRepository;
import com.example.testagent.domain.session.SessionStatus;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.domain.workspace.WorkspaceRepository;
import com.example.testagent.domain.workspace.WorkspaceStatus;
import com.example.testagent.event.RunEventAppender;
import com.example.testagent.opencode.client.OpencodeCancelCommand;
import com.example.testagent.opencode.client.OpencodeCancelResult;
import com.example.testagent.opencode.client.OpencodeClientFacade;
import com.example.testagent.opencode.client.OpencodeHealthCommand;
import com.example.testagent.opencode.client.OpencodeHealthResult;
import com.example.testagent.opencode.client.OpencodeStartRunCommand;
import com.example.testagent.opencode.client.OpencodeStartRunResult;
import com.example.testagent.opencode.client.OpencodeStreamEventsCommand;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RunApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void serviceCreatesRunRoutesNodeStartsOpencodeAndAppendsEvents() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                facade);

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(runs.saved).extracting(Run::status).contains(RunStatus.PENDING, RunStatus.RUNNING);
        assertThat(facade.lastPrompt).isEqualTo("run the tests");
        assertThat(events.events).extracting(RunEvent::type)
                .containsExactly(RunEventType.RUN_CREATED, RunEventType.RUN_STARTED);
    }

    @Test
    void serviceCancelsPendingRunWithoutSecondCancelTransition() {
        FakeRunRepository runs = new FakeRunRepository();
        Run pending = new Run(
                new RunId("run_1234567890abcdef"),
                session().sessionId(),
                workspace().workspaceId(),
                RunStatus.PENDING,
                NOW,
                NOW,
                "trace_1234567890abcdef");
        runs.save(pending);
        FakeRunEventRepository events = new FakeRunEventRepository();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                new FakeOpencodeFacade());

        Run cancelled = service.cancelRun(pending.runId(), "trace_1234567890abcdef");

        assertThat(cancelled.status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(events.events).extracting(RunEvent::type)
                .containsExactly(RunEventType.RUN_CANCELLED);
    }

    private static Workspace workspace() {
        return new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "Demo",
                "/tmp/demo",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static Session session() {
        return new Session(
                new SessionId("ses_1234567890abcdef"),
                workspace().workspaceId(),
                "Demo session",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static ExecutionNode node() {
        return new ExecutionNode(
                new ExecutionNodeId("node_1234567890abcdef"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                100,
                NOW,
                Set.of("chat"),
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static final class FakeWorkspaceRepository implements WorkspaceRepository {
        @Override
        public Workspace save(Workspace workspace) {
            return workspace;
        }

        @Override
        public Optional<Workspace> findById(WorkspaceId workspaceId) {
            return Optional.of(workspace());
        }

        @Override
        public PageResponse<Workspace> findPage(PageRequest pageRequest) {
            return new PageResponse<>(List.of(workspace()), pageRequest.page(), pageRequest.size(), 1);
        }
    }

    private static final class FakeSessionRepository implements com.example.testagent.domain.session.SessionRepository {
        @Override
        public Session save(Session session) {
            return session;
        }

        @Override
        public Optional<Session> findById(SessionId sessionId) {
            return Optional.of(session());
        }

        @Override
        public PageResponse<Session> findByWorkspaceId(WorkspaceId workspaceId, PageRequest pageRequest) {
            return new PageResponse<>(List.of(session()), pageRequest.page(), pageRequest.size(), 1);
        }
    }

    private static final class FakeRunRepository implements RunRepository {
        private final List<Run> saved = new ArrayList<>();

        @Override
        public Run save(Run run) {
            saved.add(run);
            return run;
        }

        @Override
        public Optional<Run> findById(RunId runId) {
            return saved.stream().filter(run -> run.runId().equals(runId)).reduce((first, second) -> second);
        }
    }

    private static final class FakeSessionMessageRepository implements SessionMessageRepository {
        @Override
        public SessionMessage save(SessionMessage message) {
            return message;
        }

        @Override
        public Optional<SessionMessage> findById(SessionMessageId messageId) {
            return Optional.empty();
        }

        @Override
        public PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest) {
            return new PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
        }
    }

    private static final class FakeExecutionNodeRepository implements ExecutionNodeRepository {
        @Override
        public ExecutionNode save(ExecutionNode executionNode) {
            return executionNode;
        }

        @Override
        public Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId) {
            return Optional.of(node());
        }

        @Override
        public List<ExecutionNode> findRoutableNodes(int limit) {
            return List.of(node());
        }
    }

    private static final class FakeRoutingDecisionRepository implements RoutingDecisionRepository {
        @Override
        public RoutingDecision save(RoutingDecision routingDecision) {
            return routingDecision;
        }

        @Override
        public Optional<RoutingDecision> findByRunId(RunId runId) {
            return Optional.empty();
        }
    }

    private static final class FakeRunEventRepository implements RunEventRepository {
        private final List<RunEvent> events = new ArrayList<>();

        @Override
        public RunEvent append(RunEventDraft draft) {
            RunEvent event = new RunEvent(
                    new RunEventId("evt_" + (events.size() + 1)),
                    draft.runId(),
                    events.size() + 1L,
                    draft.type(),
                    draft.traceId(),
                    draft.occurredAt(),
                    draft.payload());
            events.add(event);
            return event;
        }

        @Override
        public List<RunEvent> findByRunIdAfter(RunId runId, long lastSeq, int limit) {
            return events.stream().filter(event -> event.seq() > lastSeq).limit(limit).toList();
        }
    }

    private static final class FakeOpencodeFacade implements OpencodeClientFacade {
        private String lastPrompt;

        @Override
        public Mono<OpencodeHealthResult> health(OpencodeHealthCommand command) {
            return Mono.just(new OpencodeHealthResult(true, command.node().baseUrl()));
        }

        @Override
        public Mono<OpencodeCancelResult> cancelSession(OpencodeCancelCommand command) {
            return Mono.just(new OpencodeCancelResult(true));
        }

        @Override
        public Mono<OpencodeStartRunResult> startRun(OpencodeStartRunCommand command) {
            lastPrompt = command.prompt();
            return Mono.just(new OpencodeStartRunResult(true));
        }

        @Override
        public Flux<RunEventDraft> streamRunEvents(OpencodeStreamEventsCommand command) {
            return Flux.empty();
        }
    }
}
