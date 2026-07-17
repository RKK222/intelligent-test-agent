package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.OpencodeAgentRuntime;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.event.RunEvent;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventId;
import com.enterprise.testagent.domain.event.RunEventRepository;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunDetailsLocator;
import com.enterprise.testagent.domain.run.RunDiffAction;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeAppendResult;
import com.enterprise.testagent.domain.run.RunRuntimeReplay;
import com.enterprise.testagent.domain.run.RunRuntimeSnapshot;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunSummaryPersistencePort;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.opencode.client.OpencodeCancelCommand;
import com.enterprise.testagent.opencode.client.OpencodeCancelResult;
import com.enterprise.testagent.opencode.client.OpencodeCreateSessionCommand;
import com.enterprise.testagent.opencode.client.OpencodeCreateSessionResult;
import com.enterprise.testagent.opencode.client.OpencodeDiffCommand;
import com.enterprise.testagent.opencode.client.OpencodeDiffFile;
import com.enterprise.testagent.opencode.client.OpencodeDiffResult;
import com.enterprise.testagent.opencode.client.OpencodeHealthCommand;
import com.enterprise.testagent.opencode.client.OpencodeHealthResult;
import com.enterprise.testagent.opencode.client.OpencodeRejectDiffCommand;
import com.enterprise.testagent.opencode.client.OpencodeRejectDiffResult;
import com.enterprise.testagent.opencode.client.OpencodeRuntimeCommand;
import com.enterprise.testagent.opencode.client.OpencodeRuntimeResult;
import com.enterprise.testagent.opencode.client.OpencodeSessionExistsCommand;
import com.enterprise.testagent.opencode.client.OpencodeSessionMessagesCommand;
import com.enterprise.testagent.opencode.client.OpencodeSessionMessagesResult;
import com.enterprise.testagent.opencode.client.OpencodeStartCommand;
import com.enterprise.testagent.opencode.client.OpencodeStartRunCommand;
import com.enterprise.testagent.opencode.client.OpencodeStartRunResult;
import com.enterprise.testagent.opencode.client.OpencodeStreamEventsCommand;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    @Test
    void redisSummaryReadsMaterializedDiffWithoutQueryingLegacyRunEvents() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort summaryPersistence = mock(RunSummaryPersistencePort.class);
        when(runtimeStore.findManifest(run().runId())).thenReturn(Optional.of(redisSummaryManifest()));
        when(runtimeStore.replayAfter(run().runId(), 0L, 1)).thenReturn(new RunRuntimeReplay(
                redisSummaryManifest(),
                new RunRuntimeSnapshot(
                        run().runId(),
                        3L,
                        3L,
                        0L,
                        List.of(diffProposed(Map.of("files", List.of(Map.of("path", "src/Redis.ts"))))),
                        NOW),
                List.of(),
                false,
                null));
        RunDiffApplicationService service = service(
                events, new FakeOpencodeFacade(), mappedSession(), runtimeStore, summaryPersistence);

        RunDiffResponse response = service.getDiff(run().runId(), "trace_1234567890abcdef");

        assertThat(response.files()).extracting(RunDiffFileResponse::path).containsExactly("src/Redis.ts");
        assertThat(events.readCount).isZero();
        verify(summaryPersistence, never()).findDetailsLocator(run().runId());
    }

    @Test
    void redisSummaryReturnsDetailsExpiredInsteadOfFallingBackToLegacyEvents() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort summaryPersistence = mock(RunSummaryPersistencePort.class);
        when(runtimeStore.findManifest(run().runId())).thenReturn(Optional.empty());
        when(summaryPersistence.findDetailsLocator(run().runId())).thenReturn(Optional.of(detailsLocator()));
        RunDiffApplicationService service = service(
                events, new FakeOpencodeFacade(), mappedSession(), runtimeStore, summaryPersistence);

        assertThatThrownBy(() -> service.getDiff(run().runId(), "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RUN_DETAILS_EXPIRED));
        assertThat(events.readCount).isZero();
    }

    @Test
    void legacyDiffStillUsesDatabaseEventsWhenRedisIsUnavailable() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        events.append(diffProposed(Map.of("files", List.of(Map.of("path", "src/Legacy.ts")))));
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort summaryPersistence = mock(RunSummaryPersistencePort.class);
        when(runtimeStore.findManifest(run().runId()))
                .thenThrow(new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Redis unavailable"));
        when(summaryPersistence.findDetailsLocator(run().runId())).thenReturn(Optional.empty());
        RunDiffApplicationService service = service(
                events, new FakeOpencodeFacade(), mappedSession(), runtimeStore, summaryPersistence);

        RunDiffResponse response = service.getDiff(run().runId(), "trace_1234567890abcdef");

        assertThat(response.files()).extracting(RunDiffFileResponse::path).containsExactly("src/Legacy.ts");
        assertThat(events.readCount).isEqualTo(1);
    }

    @Test
    void redisSummaryAcceptAppendsRedisEventThenUpdatesRunCounterWithoutLegacyEventWrite() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort summaryPersistence = mock(RunSummaryPersistencePort.class);
        when(runtimeStore.findManifest(run().runId())).thenReturn(Optional.of(redisSummaryManifest()));
        when(runtimeStore.replayAfter(run().runId(), 0L, 1)).thenReturn(new RunRuntimeReplay(
                redisSummaryManifest(),
                new RunRuntimeSnapshot(
                        run().runId(),
                        3L,
                        3L,
                        0L,
                        List.of(diffProposed(Map.of("files", List.of(Map.of("path", "src/Redis.ts"))))),
                        NOW),
                List.of(),
                false,
                null));
        when(runtimeStore.appendDurable(any())).thenAnswer(invocation -> {
            RunEventDraft draft = invocation.getArgument(0);
            return new RunRuntimeAppendResult(new RunEvent(
                    new RunEventId("evt_redis_diff_action"),
                    draft.runId(),
                    4L,
                    draft.type(),
                    draft.traceId(),
                    draft.occurredAt(),
                    draft.payload()), false, 0L, 1L);
        });
        when(summaryPersistence.recordDiffAction(run().runId(), RunDiffAction.ACCEPTED)).thenReturn(true);
        RunDiffApplicationService service = service(
                events, new FakeOpencodeFacade(), mappedSession(), runtimeStore, summaryPersistence);

        RunDiffActionResponse response = service.acceptDiff(run().runId(), "trace_1234567890abcdef");

        assertThat(response.status()).isEqualTo("accepted");
        assertThat(events.events).isEmpty();
        verify(runtimeStore).appendDurable(org.mockito.ArgumentMatchers.argThat(
                draft -> draft.type() == RunEventType.DIFF_ACCEPTED));
        verify(summaryPersistence).recordDiffAction(run().runId(), RunDiffAction.ACCEPTED);
    }

    private static RunDiffApplicationService service(
            FakeRunEventRepository events,
            FakeOpencodeFacade facade,
            Session session) {
        return service(events, facade, session, null, null);
    }

    private static RunDiffApplicationService service(
            FakeRunEventRepository events,
            FakeOpencodeFacade facade,
            Session session,
            RunRuntimeStore runtimeStore,
            RunSummaryPersistencePort summaryPersistence) {
        RunEventAppender appender = runtimeStore == null
                ? new RunEventAppender(events)
                : new RunEventAppender(events, null, runtimeStore);
        return new RunDiffApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session),
                new FakeRunRepository(),
                events,
                new FakeExecutionNodeRepository(),
                appender,
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver.legacyOnly(),
                runtimeStore,
                summaryPersistence);
    }

    private static AgentRuntimeRegistry runtimeRegistry(FakeOpencodeFacade facade) {
        return new AgentRuntimeRegistry(List.of(new OpencodeAgentRuntime(facade)));
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

    private static RunRuntimeManifest redisSummaryManifest() {
        return new RunRuntimeManifest(
                run().runId(),
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_diff_runtime"),
                run().sessionId(),
                run().workspaceId(),
                "opencode",
                "request-diff-runtime",
                "msg_dispatch_diff_runtime",
                "server-a",
                "backend-a",
                node().executionNodeId().value(),
                "opc_diff_runtime",
                "ses_remote1234567890abcdef",
                RunStatus.SUCCEEDED,
                2L,
                3L,
                1L,
                0L,
                false,
                3L,
                1_024L,
                null,
                null,
                null,
                NOW.plusSeconds(86_400),
                NOW,
                NOW.plusSeconds(10));
    }

    private static RunDetailsLocator detailsLocator() {
        return new RunDetailsLocator(
                run().runId(),
                RunStorageMode.REDIS_SUMMARY,
                "ses_remote1234567890abcdef",
                "msg_dispatch1234567890abcdef",
                node().executionNodeId().value(),
                "msg_remote1234567890abcdef",
                "prt_remote1234567890abcdef",
                NOW.plusSeconds(86_400));
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

    private static final class FakeSessionRepository implements com.enterprise.testagent.domain.session.SessionRepository {
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
        private int readCount;

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
            readCount++;
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

    private static final class FakeAgentSessionBindingRepository implements AgentSessionBindingRepository {
        private final Map<String, AgentSessionBinding> bindings = new LinkedHashMap<>();

        @Override
        public AgentSessionBinding save(AgentSessionBinding binding) {
            bindings.put(key(binding.sessionId(), binding.agentId()), binding);
            return binding;
        }

        @Override
        public Optional<AgentSessionBinding> findBySessionIdAndAgentId(SessionId sessionId, String agentId) {
            return Optional.ofNullable(bindings.get(key(sessionId, agentId)));
        }

        @Override
        public Optional<AgentSessionBinding> findByAgentIdAndRemoteSessionId(String agentId, String remoteSessionId) {
            return bindings.values().stream()
                    .filter(binding -> binding.agentId().equals(agentId.trim().toLowerCase(Locale.ROOT)))
                    .filter(binding -> binding.remoteSessionId().equals(remoteSessionId))
                    .findFirst();
        }

        private String key(SessionId sessionId, String agentId) {
            return sessionId.value() + ":" + agentId.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static final class FakeOpencodeFacade implements com.enterprise.testagent.opencode.client.OpencodeClientFacade {
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
        public Mono<Boolean> sessionExists(OpencodeSessionExistsCommand command) {
            return Mono.just(true);
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
        public Mono<OpencodeStartRunResult> startCommand(OpencodeStartCommand command) {
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
