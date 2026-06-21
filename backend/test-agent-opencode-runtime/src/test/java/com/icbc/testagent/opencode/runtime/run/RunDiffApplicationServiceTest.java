package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventId;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.event.RunEventAppender;
import com.icbc.testagent.opencode.client.OpencodeCancelCommand;
import com.icbc.testagent.opencode.client.OpencodeCancelResult;
import com.icbc.testagent.opencode.client.OpencodeCreateSessionCommand;
import com.icbc.testagent.opencode.client.OpencodeCreateSessionResult;
import com.icbc.testagent.opencode.client.OpencodeDiffCommand;
import com.icbc.testagent.opencode.client.OpencodeDiffFile;
import com.icbc.testagent.opencode.client.OpencodeDiffResult;
import com.icbc.testagent.opencode.client.OpencodeHealthCommand;
import com.icbc.testagent.opencode.client.OpencodeHealthResult;
import com.icbc.testagent.opencode.client.OpencodeRejectDiffCommand;
import com.icbc.testagent.opencode.client.OpencodeRejectDiffResult;
import com.icbc.testagent.opencode.client.OpencodeRuntimeCommand;
import com.icbc.testagent.opencode.client.OpencodeRuntimeResult;
import com.icbc.testagent.opencode.client.OpencodeSessionMessagesCommand;
import com.icbc.testagent.opencode.client.OpencodeSessionMessagesResult;
import com.icbc.testagent.opencode.client.OpencodeStartRunCommand;
import com.icbc.testagent.opencode.client.OpencodeStartRunResult;
import com.icbc.testagent.opencode.client.OpencodeStreamEventsCommand;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RunDiffApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void serviceReadsDiffFromLatestRunEventBeforeCallingOpencode() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        events.append(diffProposed(Map.of(
                "diff",
                List.of(Map.of(
                        "file", "src/App.tsx",
                        "patch", "@@ -1 +1 @@\n-old\n+new\n",
                        "additions", 2,
                        "deletions", 1,
                        "status", "modified")))));
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunDiffApplicationService service = service(events, facade, mappedSession());

        RunDiffResponse response = service.getDiff(run().runId(), "trace_1234567890abcdef");

        assertThat(response.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("src/App.tsx");
            assertThat(file.patch()).contains("@@");
            assertThat(file.additions()).isEqualTo(2);
            assertThat(file.deletions()).isEqualTo(1);
            assertThat(file.status()).isEqualTo("modified");
        });
        assertThat(facade.getDiffCommands).isEmpty();
    }

    @Test
    void serviceAcceptsRunDiffByAppendingAcceptedEvent() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        events.append(diffProposed(Map.of(
                "files",
                List.of(Map.of("path", "src/App.tsx"), Map.of("path", "src/Button.tsx")))));
        RunDiffApplicationService service = service(events, new FakeOpencodeFacade(), mappedSession());

        RunDiffActionResponse response = service.acceptDiff(run().runId(), "trace_1234567890abcdef");

        assertThat(response.action()).isEqualTo("accept");
        assertThat(response.status()).isEqualTo("accepted");
        assertThat(response.fileCount()).isEqualTo(2);
        assertThat(events.events).extracting(RunEvent::type).contains(RunEventType.DIFF_ACCEPTED);
        assertThat(events.events.getLast().payload()).containsEntry("action", "accept");
    }

    @Test
    void serviceRejectsRunDiffThroughOpencodeRevertAndAppendsRejectedEvent() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        events.append(new RunEventDraft(
                run().runId(),
                RunEventType.RUN_STARTED,
                "trace_1234567890abcdef",
                NOW,
                Map.of("messageID", "msg_remote1234567890abcdef", "partID", "prt_remote1234567890abcdef")));
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunDiffApplicationService service = service(events, facade, mappedSession());

        RunDiffActionResponse response = service.rejectDiff(run().runId(), "trace_1234567890abcdef");

        assertThat(response.action()).isEqualTo("reject");
        assertThat(response.status()).isEqualTo("rejected");
        assertThat(facade.rejectDiffCommands).singleElement().satisfies(command -> {
            assertThat(command.opencodeSessionId()).isEqualTo("ses_remote1234567890abcdef");
            assertThat(command.messageId()).isEqualTo("msg_remote1234567890abcdef");
            assertThat(command.partId()).isEqualTo("prt_remote1234567890abcdef");
            assertThat(command.workspace()).isNull();
        });
        assertThat(events.events).extracting(RunEvent::type).contains(RunEventType.DIFF_REJECTED);
    }

    @Test
    void serviceRejectDiffReturnsConflictWhenMessageIdIsMissing() {
        RunDiffApplicationService service = service(new FakeRunEventRepository(), new FakeOpencodeFacade(), mappedSession());

        assertThatThrownBy(() -> service.rejectDiff(run().runId(), "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void serviceFallsBackToOpencodeDiffWhenNoDiffEventExists() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunDiffApplicationService service = service(new FakeRunEventRepository(), facade, mappedSession());

        RunDiffResponse response = service.getDiff(run().runId(), "trace_1234567890abcdef");

        assertThat(response.files()).singleElement().satisfies(file -> assertThat(file.path()).isEqualTo("tests/demo.spec.ts"));
        assertThat(facade.getDiffCommands).singleElement().satisfies(command -> {
            assertThat(command.opencodeSessionId()).isEqualTo("ses_remote1234567890abcdef");
            assertThat(command.messageId()).isNull();
            assertThat(command.workspace()).isNull();
        });
    }

    private static RunDiffApplicationService service(
            FakeRunEventRepository events,
            FakeOpencodeFacade facade,
            Session session) {
        return new RunDiffApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session),
                new FakeRunRepository(),
                events,
                new FakeExecutionNodeRepository(),
                new RunEventAppender(events),
                facade);
    }

    private static RunEventDraft diffProposed(Map<String, Object> payload) {
        return new RunEventDraft(
                run().runId(),
                RunEventType.DIFF_PROPOSED,
                "trace_1234567890abcdef",
                NOW,
                payload);
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

    private static Run run() {
        return new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                workspace().workspaceId(),
                RunStatus.RUNNING,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static Session mappedSession() {
        return new Session(
                new SessionId("ses_1234567890abcdef"),
                workspace().workspaceId(),
                "Demo session",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef")
                .attachOpencodeSession(
                        "ses_remote1234567890abcdef",
                        node().executionNodeId(),
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

    private static final class FakeSessionRepository implements com.icbc.testagent.domain.session.SessionRepository {
        private final Session session;

        private FakeSessionRepository(Session session) {
            this.session = session;
        }

        @Override
        public Session save(Session session) {
            return session;
        }

        @Override
        public Optional<Session> findById(SessionId sessionId) {
            return Optional.of(session);
        }

        @Override
        public PageResponse<Session> findPage(String query, PageRequest pageRequest) {
            return new PageResponse<>(List.of(session), pageRequest.page(), pageRequest.size(), 1);
        }

        @Override
        public PageResponse<Session> findByWorkspaceId(WorkspaceId workspaceId, PageRequest pageRequest) {
            return new PageResponse<>(List.of(session), pageRequest.page(), pageRequest.size(), 1);
        }

        @Override
        public Optional<Session> attachOpencodeSession(
                SessionId sessionId,
                String opencodeSessionId,
                ExecutionNodeId executionNodeId,
                Instant updatedAt,
                String traceId) {
            return Optional.of(session);
        }
    }

    private static final class FakeRunRepository implements RunRepository {
        @Override
        public Run save(Run run) {
            return run;
        }

        @Override
        public Optional<Run> findById(RunId runId) {
            return Optional.of(run());
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
            return events.stream()
                    .filter(event -> event.runId().equals(runId))
                    .filter(event -> event.seq() > lastSeq)
                    .limit(limit)
                    .toList();
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

    private static final class FakeOpencodeFacade implements com.icbc.testagent.opencode.client.OpencodeClientFacade {
        private final List<OpencodeDiffCommand> getDiffCommands = new ArrayList<>();
        private final List<OpencodeRejectDiffCommand> rejectDiffCommands = new ArrayList<>();

        @Override
        public Mono<OpencodeHealthResult> health(OpencodeHealthCommand command) {
            return Mono.just(new OpencodeHealthResult(true, command.node().baseUrl()));
        }

        @Override
        public Mono<OpencodeCreateSessionResult> createSession(OpencodeCreateSessionCommand command) {
            return Mono.just(new OpencodeCreateSessionResult("ses_remote1234567890abcdef"));
        }

        @Override
        public Mono<OpencodeCancelResult> cancelSession(OpencodeCancelCommand command) {
            return Mono.just(new OpencodeCancelResult(true));
        }

        @Override
        public Mono<OpencodeStartRunResult> startRun(OpencodeStartRunCommand command) {
            return Mono.just(new OpencodeStartRunResult(true));
        }

        @Override
        public Flux<RunEventDraft> streamRunEvents(OpencodeStreamEventsCommand command) {
            return Flux.empty();
        }

        @Override
        public Mono<OpencodeDiffResult> getDiff(OpencodeDiffCommand command) {
            getDiffCommands.add(command);
            return Mono.just(new OpencodeDiffResult(List.of(new OpencodeDiffFile(
                    "tests/demo.spec.ts",
                    "@@ -1 +1 @@\n-old\n+new\n",
                    1,
                    1,
                    "modified"))));
        }

        @Override
        public Mono<OpencodeRejectDiffResult> rejectDiff(OpencodeRejectDiffCommand command) {
            rejectDiffCommands.add(command);
            return Mono.just(new OpencodeRejectDiffResult(true));
        }

        @Override
        public Mono<OpencodeRuntimeResult> runtime(OpencodeRuntimeCommand command) {
            return Mono.just(new OpencodeRuntimeResult(JsonNodeFactory.instance.objectNode()));
        }

        @Override
        public Mono<OpencodeSessionMessagesResult> sessionMessages(OpencodeSessionMessagesCommand command) {
            return Mono.just(new OpencodeSessionMessagesResult(List.of(), null, null));
        }
    }
}
