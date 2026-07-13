package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.OpencodeAgentRuntime;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventId;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.event.RunSessionScope;
import com.icbc.testagent.domain.event.RunSessionScopeRepository;
import com.icbc.testagent.domain.event.RunSessionScopeSession;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.routing.RoutingDecision;
import com.icbc.testagent.domain.routing.RoutingDecisionRepository;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunDetailsLocator;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunRuntimeAppendResult;
import com.icbc.testagent.domain.run.RunSummaryPersistencePort;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.event.RunEventAppender;
import com.icbc.testagent.event.RunEventLiveBus;
import com.icbc.testagent.event.RunEventLiveEvent;
import com.icbc.testagent.event.RunEventSsePayload;
import com.icbc.testagent.opencode.runtime.model.ModelCatalogApplicationService;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.opencode.client.OpencodeCancelCommand;
import com.icbc.testagent.opencode.client.OpencodeCancelResult;
import com.icbc.testagent.opencode.client.OpencodeClientFacade;
import com.icbc.testagent.opencode.client.OpencodeCreateSessionCommand;
import com.icbc.testagent.opencode.client.OpencodeCreateSessionResult;
import com.icbc.testagent.opencode.client.OpencodeDiffCommand;
import com.icbc.testagent.opencode.client.OpencodeDiffResult;
import com.icbc.testagent.opencode.client.OpencodeHealthCommand;
import com.icbc.testagent.opencode.client.OpencodeHealthResult;
import com.icbc.testagent.opencode.client.OpencodePromptPart;
import com.icbc.testagent.opencode.client.OpencodeRejectDiffCommand;
import com.icbc.testagent.opencode.client.OpencodeRejectDiffResult;
import com.icbc.testagent.opencode.client.OpencodeRuntimeCommand;
import com.icbc.testagent.opencode.client.OpencodeRuntimeResult;
import com.icbc.testagent.opencode.client.OpencodeSessionExistsCommand;
import com.icbc.testagent.opencode.client.OpencodeSessionMessage;
import com.icbc.testagent.opencode.client.OpencodeSessionMessagesCommand;
import com.icbc.testagent.opencode.client.OpencodeSessionMessagesResult;
import com.icbc.testagent.opencode.client.OpencodeStartCommand;
import com.icbc.testagent.opencode.client.OpencodeStartRunCommand;
import com.icbc.testagent.opencode.client.OpencodeStartRunResult;
import com.icbc.testagent.opencode.client.OpencodeStreamEventsCommand;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RunApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private static final String REMOTE_SESSION_ID = "ses_remote1234567890abcdef";

    @Test
    void serviceCreatesRemoteOpencodeSessionOnFirstRunAndDoesNotSendPlatformWorkspace() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        FakeAgentSessionBindingRepository bindings = new FakeAgentSessionBindingRepository();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                bindings);

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(runs.saved).extracting(Run::status).contains(RunStatus.PENDING, RunStatus.RUNNING);
        assertThat(facade.lastPrompt).isEqualTo("run the tests");
        assertThat(facade.createSessionCommands).hasSize(1);
        assertThat(facade.createSessionCommands.getFirst().directory()).isEqualTo("/tmp/demo");
        assertThat(facade.createSessionCommands.getFirst().workspace()).isNull();
        assertThat(facade.createSessionCommands.getFirst().title()).isNull();
        assertThat(facade.startRunCommands).hasSize(1);
        assertThat(facade.startRunCommands.getFirst().opencodeSessionId()).isEqualTo(REMOTE_SESSION_ID);
        assertThat(facade.startRunCommands.getFirst().workspace()).isNull();
        assertThat(facade.startRunCommands.getFirst().agent()).isEqualTo("build");
        assertThat(facade.callOrder).containsSubsequence("streamRunEvents", "startRun");
        assertThat(sessions.current.opencodeSessionId()).isEqualTo(REMOTE_SESSION_ID);
        assertThat(sessions.current.opencodeExecutionNodeId()).isEqualTo(node().executionNodeId());
        assertThat(bindings.findBySessionIdAndAgentId(new SessionId("ses_1234567890abcdef"), "opencode"))
                .get()
                .extracting(AgentSessionBinding::remoteSessionId)
                .isEqualTo(REMOTE_SESSION_ID);
        assertThat(events.events).extracting(RunEvent::type)
                .containsExactly(RunEventType.RUN_CREATED, RunEventType.RUN_STARTED);
    }

    @Test
    void serviceReturnsRunningBeforeRemotePromptRequestCompletes() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.startRun = ignored -> Mono.never();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository()),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = assertTimeoutPreemptively(
                Duration.ofMillis(500),
                () -> service.startRun(
                        new SessionId("ses_1234567890abcdef"),
                        "run the tests",
                        "trace_1234567890abcdef"));

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(facade.startRunCommands).hasSize(1);
    }

    @Test
    void serviceForwardsSlashCommandThroughRecoverableRun() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository()),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(new StartRunInput(
                        new SessionId("ses_1234567890abcdef"),
                        "/test-design-path 对车贷的开发文档，生成路径图",
                        List.of(StartRunInput.PromptPart.text("/test-design-path 对车贷的开发文档，生成路径图")),
                        null,
                        "build",
                        "opencode/north-mini-code-free",
                        null,
                        "build",
                        "test-design-path",
                        "对车贷的开发文档，生成路径图"),
                "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(facade.startCommandCommands).singleElement().satisfies(command -> {
            assertThat(command.command()).isEqualTo("test-design-path");
            assertThat(command.arguments()).isEqualTo("对车贷的开发文档，生成路径图");
        });
    }

    @Test
    void serviceMarksRunFailedWhenRemotePromptRequestFailsAsynchronously() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.startRun = ignored -> Mono.error(new IllegalStateException("prompt failed"));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(
                new SessionId("ses_1234567890abcdef"),
                "run the tests",
                "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        awaitRunStatus(service, run.runId(), RunStatus.FAILED);
        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.RUN_FAILED);
        Map<String, Object> payload = events.events.get(2).payload();
        assertThat(payload).containsEntry("message", "prompt failed");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) payload.get("error");
        assertThat(error)
                .containsEntry("name", "IllegalStateException")
                .containsEntry("message", "prompt failed");
    }

    @Test
    void userAwareRunUsesAssignedOpencodeProcessNodeAndAvoidsLegacyRouting() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeExecutionNodeRepository nodes = new FakeExecutionNodeRepository();
        FakeSessionMessageRepository messages = new FakeSessionMessageRepository();
        UserOpencodeProcessAssignmentService assignmentService = org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        ExecutionNode assignedNode = userProcessNode("node_1234567890abcdef", "http://10.8.0.12:4096");
        org.mockito.Mockito.when(assignmentService.requireReadyProcess(
                        new UserId("usr_1234567890abcdef"),
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(assignedNode));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                messages,
                nodes,
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                assignmentService);

        Run run = service.startRun(
                new UserId("usr_1234567890abcdef"),
                new StartRunInput(new SessionId("ses_1234567890abcdef"), "run the tests", List.of(), null, null, null, null, null),
                "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(run.triggeredByUserId()).isEqualTo(new UserId("usr_1234567890abcdef"));
        assertThat(runs.saved).allSatisfy(saved ->
                assertThat(saved.triggeredByUserId()).isEqualTo(new UserId("usr_1234567890abcdef")));
        assertThat(messages.saved).singleElement().satisfies(message ->
                assertThat(message.senderUserId()).isEqualTo(new UserId("usr_1234567890abcdef")));
        assertThat(nodes.findRoutableNodesCalls).isZero();
        assertThat(nodes.saved).contains(assignedNode);
        assertThat(facade.createSessionCommands).hasSize(1);
        assertThat(facade.createSessionCommands.getFirst().node().baseUrl()).isEqualTo("http://10.8.0.12:4096");
        assertThat(facade.startRunCommands.getFirst().node().baseUrl()).isEqualTo("http://10.8.0.12:4096");
    }

    @Test
    void contextBackedExistingSessionStartsWithoutRepeatedControlPlaneReads() {
        UserId userId = new UserId("usr_1234567890abcdef");
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        FakeExecutionNodeRepository nodes = new FakeExecutionNodeRepository();
        FakeAgentSessionBindingRepository bindings = new FakeAgentSessionBindingRepository();
        UserOpencodeProcessAssignmentService assignmentService = org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        ConversationRunContextResolver contextResolver = org.mockito.Mockito.mock(ConversationRunContextResolver.class);
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        Workspace workspaceSnapshot = workspace().withLinuxServerId(
                "server-a",
                "trace_1234567890abcdef",
                NOW);
        ExecutionNode nodeSnapshot = userProcessNode(
                "node_ocp_1234567890abcdef",
                "http://10.8.0.12:4096");
        Session sessionSnapshot = session().attachOpencodeSession(
                REMOTE_SESSION_ID,
                nodeSnapshot.executionNodeId(),
                NOW,
                "trace_1234567890abcdef");
        AgentSessionBinding bindingSnapshot = new AgentSessionBinding(
                sessionSnapshot.sessionId(),
                "opencode",
                REMOTE_SESSION_ID,
                nodeSnapshot.executionNodeId(),
                NOW,
                NOW,
                "trace_1234567890abcdef");
        ConversationRunContext context = new ConversationRunContext(
                userId,
                "opencode",
                "ocp_1234567890abcdef",
                "server-a",
                sessionSnapshot,
                workspaceSnapshot,
                nodeSnapshot,
                bindingSnapshot,
                1,
                NOW.plusSeconds(3600));
        StartRunInput input = new StartRunInput(
                sessionSnapshot.sessionId(),
                "run the tests",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "ctx_secret",
                "request-123");
        org.mockito.Mockito.when(contextResolver.resolve(
                        userId,
                        "opencode",
                        input,
                        "trace_1234567890abcdef"))
                .thenReturn(Optional.of(context));
        RunApplicationService service = new RunApplicationService(
                workspaces,
                sessions,
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                nodes,
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository()),
                runtimeRegistry(facade),
                bindings,
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                assignmentService,
                ManagedWorkspacePathResolver.legacyOnly(),
                org.mockito.Mockito.mock(RunSessionMessageSnapshotService.class),
                null,
                null,
                null,
                contextResolver);

        Run run = service.startRun(userId, "opencode", input, "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(workspaces.findByIdCalls).isZero();
        assertThat(sessions.findByIdCalls).isZero();
        assertThat(nodes.findByIdCalls).isZero();
        assertThat(nodes.saved).isEmpty();
        assertThat(bindings.findBySessionIdAndAgentIdCalls).isZero();
        org.mockito.Mockito.verifyNoInteractions(assignmentService);
        org.mockito.Mockito.verify(contextResolver).resolve(
                userId,
                "opencode",
                input,
                "trace_1234567890abcdef");
        assertThat(facade.createSessionCommands).isEmpty();
        assertThat(facade.startRunCommands).singleElement().satisfies(command -> {
            assertThat(command.node()).isEqualTo(nodeSnapshot);
            assertThat(command.opencodeSessionId()).isEqualTo(REMOTE_SESSION_ID);
            assertThat(command.directory()).isEqualTo("/tmp/demo");
        });
    }

    @Test
    void redisSummaryExistingSessionStartsAndCancelsWithoutLegacyWrites() {
        UserId userId = new UserId("usr_1234567890abcdef");
        Workspace workspaceSnapshot = workspace().withLinuxServerId("server-a", "trace_redis_summary", NOW);
        ExecutionNode nodeSnapshot = userProcessNode(
                "node_ocp_1234567890abcdef", "http://10.8.0.12:4096");
        Session sessionSnapshot = session().attachOpencodeSession(
                REMOTE_SESSION_ID, nodeSnapshot.executionNodeId(), NOW, "trace_redis_summary");
        AgentSessionBinding bindingSnapshot = new AgentSessionBinding(
                sessionSnapshot.sessionId(), "opencode", REMOTE_SESSION_ID, nodeSnapshot.executionNodeId(),
                NOW, NOW, "trace_redis_summary");
        ConversationRunContext context = new ConversationRunContext(
                userId, "opencode", "ocp_1234567890abcdef", "server-a",
                sessionSnapshot, workspaceSnapshot, nodeSnapshot, bindingSnapshot, 1, NOW.plusSeconds(3600));
        StartRunInput input = new StartRunInput(
                sessionSnapshot.sessionId(), "run the tests", List.of(), "msg_user_summary",
                null, null, null, null, null, null, "ctx_secret", "request-summary-123");
        ConversationRunContextResolver contextResolver = org.mockito.Mockito.mock(ConversationRunContextResolver.class);
        org.mockito.Mockito.when(contextResolver.resolve(userId, "opencode", input, "trace_redis_summary"))
                .thenReturn(Optional.of(context));
        RunStorageModeSelector selector = org.mockito.Mockito.mock(RunStorageModeSelector.class);
        org.mockito.Mockito.when(selector.select(userId, input, context)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        AtomicReference<RunRuntimeManifest> runtimeManifest = new AtomicReference<>();
        RunRuntimeStore runtimeStore = mockRuntimeStoreForStart(runtimeManifest);
        RunSummaryPersistencePort summaryPort = org.mockito.Mockito.mock(RunSummaryPersistencePort.class);
        com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity identity =
                org.mockito.Mockito.mock(com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity.class);
        org.mockito.Mockito.when(identity.linuxServerId()).thenReturn("server-a");
        org.mockito.Mockito.when(identity.backendProcessId()).thenReturn("bjp_summary_backend");
        RunTerminalProjectionService terminalProjectionService =
                org.mockito.Mockito.mock(RunTerminalProjectionService.class);
        RunOwnerLeaseSupervisor ownerSupervisor = org.mockito.Mockito.mock(RunOwnerLeaseSupervisor.class);
        RunOwnerLeaseSupervisor.OwnershipHandle ownership =
                org.mockito.Mockito.mock(RunOwnerLeaseSupervisor.OwnershipHandle.class);
        AtomicReference<RunOwnerLease> adoptedLease = new AtomicReference<>();
        FakeRunRepository runs = new FakeRunRepository();
        FakeSessionMessageRepository messages = new FakeSessionMessageRepository();
        FakeRoutingDecisionRepository routing = new FakeRoutingDecisionRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        AtomicReference<String> serverDispatchMessageId = new AtomicReference<>();
        org.mockito.Mockito.when(summaryPort.insertAnchor(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            assertThat(facade.createSessionCommands).isEmpty();
            assertThat(facade.startRunCommands).isEmpty();
            com.icbc.testagent.domain.run.RunPersistenceAnchor anchor = invocation.getArgument(0);
            serverDispatchMessageId.set(anchor.dispatchMessageId());
            return true;
        });
        org.mockito.Mockito.when(runtimeStore.claimOwnerLease(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("bjp_summary_backend")))
                .thenAnswer(invocation -> Optional.of(new RunOwnerLease(
                        invocation.getArgument(0),
                        "bjp_summary_backend",
                        1L,
                        NOW.plusSeconds(15))));
        org.mockito.Mockito.when(runtimeStore.claimOwnerLeaseIfUnchanged(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("bjp_summary_backend")))
                .thenAnswer(invocation -> {
                    RunRuntimeManifest expected = invocation.getArgument(0);
                    return Optional.of(new RunOwnerLease(
                            expected.runId(), "bjp_summary_backend", 2L, NOW.plusSeconds(15)));
                });
        org.mockito.Mockito.when(ownerSupervisor.adopt(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    adoptedLease.set(invocation.getArgument(0));
                    return Optional.of(ownership);
                });
        org.mockito.Mockito.when(ownership.lease()).thenAnswer(ignored -> adoptedLease.get());
        org.mockito.Mockito.when(ownership.lost()).thenReturn(Mono.never());
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                messages,
                new FakeExecutionNodeRepository(),
                routing,
                new RunEventAppender(new FakeRunEventRepository(), new RunEventLiveBus(), runtimeStore),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class),
                ManagedWorkspacePathResolver.legacyOnly(),
                org.mockito.Mockito.mock(RunSessionMessageSnapshotService.class),
                null,
                null,
                null,
                contextResolver,
                runtimeStore,
                selector,
                summaryPort,
                terminalProjectionService,
                identity,
                ownerSupervisor);

        Run run = service.startRun(userId, "opencode", input, "trace_redis_summary");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(runs.saved).isEmpty();
        assertThat(messages.saved).isEmpty();
        assertThat(routing.saved).isEmpty();
        org.mockito.Mockito.verify(summaryPort).insertAnchor(org.mockito.ArgumentMatchers.argThat(anchor ->
                anchor.storageMode() == RunStorageMode.REDIS_SUMMARY
                        && anchor.clientRequestId().equals("request-summary-123")
                        && anchor.dispatchMessageId().startsWith("msg_")
                        && !anchor.dispatchMessageId().equals("msg_user_summary")
                        && anchor.rootRemoteSessionId().equals(REMOTE_SESSION_ID)));
        org.mockito.Mockito.verify(runtimeStore).confirmClientRequest(
                sessionSnapshot.sessionId(), "request-summary-123", run.runId());
        org.mockito.Mockito.verify(runtimeStore, org.mockito.Mockito.atLeastOnce())
                .appendDurable(org.mockito.ArgumentMatchers.argThat(draft ->
                        draft.type() == RunEventType.RUN_CREATED
                                && draft.payload().get("assistantSummaryMessageId") instanceof String messageId
                                && messageId.startsWith("msg_")));
        assertThat(facade.createSessionCommands).isEmpty();
        assertThat(facade.startRunCommands).singleElement().satisfies(command -> {
            assertThat(command.messageId()).isEqualTo(serverDispatchMessageId.get());
            assertThat(command.opencodeSessionId()).isEqualTo(REMOTE_SESSION_ID);
        });
        assertThat(runtimeManifest.get().status()).isEqualTo(RunStatus.RUNNING);
        assertThat(service.eventStorageMode(run.runId())).isEqualTo(RunStorageMode.REDIS_SUMMARY);
        org.mockito.Mockito.verify(ownerSupervisor, org.mockito.Mockito.atLeast(4)).requireOwned(ownership);
        org.mockito.Mockito.verify(summaryPort, org.mockito.Mockito.never())
                .findDetailsLocator(run.runId());

        Run cancelled = service.cancelRun("opencode", run.runId(), "trace_redis_summary_cancel");

        assertThat(cancelled.status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(runs.saved).isEmpty();
        assertThat(messages.saved).isEmpty();
        assertThat(routing.saved).isEmpty();
        assertThat(facade.cancelSessionCommands).singleElement().satisfies(command ->
                assertThat(command.opencodeSessionId()).isEqualTo(REMOTE_SESSION_ID));
        org.mockito.Mockito.verify(runtimeStore).claimOwnerLeaseIfUnchanged(
                org.mockito.ArgumentMatchers.argThat(candidate ->
                        candidate.runId().equals(run.runId()) && candidate.status() == RunStatus.RUNNING),
                org.mockito.ArgumentMatchers.eq("bjp_summary_backend"));
        org.mockito.Mockito.verify(runtimeStore).appendDurable(
                org.mockito.ArgumentMatchers.argThat(draft -> draft.type() == RunEventType.RUN_CANCELLED),
                org.mockito.ArgumentMatchers.argThat(lease -> lease.fencingToken() == 2L));
        org.mockito.Mockito.verify(terminalProjectionService).project(
                org.mockito.ArgumentMatchers.eq(run.runId()),
                org.mockito.ArgumentMatchers.eq(RunStatus.CANCELLED),
                org.mockito.ArgumentMatchers.eq("USER_CANCEL"),
                org.mockito.ArgumentMatchers.eq("USER_REQUESTED"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq("trace_redis_summary_cancel"));

        runtimeManifest.set(null);
        org.mockito.Mockito.when(summaryPort.findDetailsLocator(run.runId())).thenReturn(Optional.of(
                new RunDetailsLocator(
                        run.runId(),
                        RunStorageMode.REDIS_SUMMARY,
                        REMOTE_SESSION_ID,
                        nodeSnapshot.executionNodeId().value(),
                        "msg_remote_summary",
                        "part_remote_summary",
                        NOW.plusSeconds(86_400))));
        assertThatThrownBy(() -> service.eventStorageMode(run.runId()))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RUN_DETAILS_EXPIRED));
    }

    @Test
    void redisSummaryStartupRedisFailureAfterAnchorSchedulesLossConvergence() {
        UserId userId = new UserId("usr_startup_redis_loss");
        Workspace workspaceSnapshot = workspace().withLinuxServerId("server-a", "trace_startup_loss", NOW);
        ExecutionNode nodeSnapshot = userProcessNode("node_startup_redis_loss", "http://10.8.0.12:4096");
        Session sessionSnapshot = session().attachOpencodeSession(
                REMOTE_SESSION_ID, nodeSnapshot.executionNodeId(), NOW, "trace_startup_loss");
        AgentSessionBinding bindingSnapshot = new AgentSessionBinding(
                sessionSnapshot.sessionId(), "opencode", REMOTE_SESSION_ID, nodeSnapshot.executionNodeId(),
                NOW, NOW, "trace_startup_loss");
        ConversationRunContext context = new ConversationRunContext(
                userId, "opencode", "startup_redis_loss", "server-a",
                sessionSnapshot, workspaceSnapshot, nodeSnapshot, bindingSnapshot, 1, NOW.plusSeconds(3600));
        StartRunInput input = new StartRunInput(
                sessionSnapshot.sessionId(), "run the tests", List.of(), "msg_startup_redis_loss",
                null, null, null, null, null, null, "ctx_startup_redis_loss", "request-startup-redis-loss");
        ConversationRunContextResolver contextResolver = org.mockito.Mockito.mock(ConversationRunContextResolver.class);
        org.mockito.Mockito.when(contextResolver.resolve(userId, "opencode", input, "trace_startup_redis_loss"))
                .thenReturn(Optional.of(context));
        RunStorageModeSelector selector = org.mockito.Mockito.mock(RunStorageModeSelector.class);
        org.mockito.Mockito.when(selector.select(userId, input, context)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        AtomicReference<RunRuntimeManifest> runtimeManifest = new AtomicReference<>();
        RunRuntimeStore runtimeStore = mockRuntimeStoreForStart(runtimeManifest);
        RunSummaryPersistencePort summaryPort = org.mockito.Mockito.mock(RunSummaryPersistencePort.class);
        org.mockito.Mockito.when(summaryPort.insertAnchor(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity identity =
                org.mockito.Mockito.mock(com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity.class);
        org.mockito.Mockito.when(identity.linuxServerId()).thenReturn("server-a");
        org.mockito.Mockito.when(identity.backendProcessId()).thenReturn("bjp_startup_redis_loss");
        org.mockito.Mockito.when(runtimeStore.claimOwnerLease(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("bjp_startup_redis_loss")))
                .thenThrow(new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "redis unavailable"));
        RunTerminalProjectionService projection = org.mockito.Mockito.mock(RunTerminalProjectionService.class);
        RunOwnerLeaseSupervisor supervisor = org.mockito.Mockito.mock(RunOwnerLeaseSupervisor.class);
        RunRuntimeLossConvergenceScheduler lossScheduler =
                org.mockito.Mockito.mock(RunRuntimeLossConvergenceScheduler.class);
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(), new FakeSessionRepository(session()), new FakeRunRepository(),
                new FakeSessionMessageRepository(), new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository(), new RunEventLiveBus(), runtimeStore),
                runtimeRegistry(facade), new FakeAgentSessionBindingRepository(), new RunEventLiveBus(),
                new RunEventPersistencePolicy(), null,
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class),
                ManagedWorkspacePathResolver.legacyOnly(),
                org.mockito.Mockito.mock(RunSessionMessageSnapshotService.class),
                null, null, null, contextResolver, runtimeStore, selector, summaryPort,
                projection, identity, supervisor);
        service.configureRuntimeLossScheduler(lossScheduler);

        assertThatThrownBy(() -> service.startRun(userId, "opencode", input, "trace_startup_redis_loss"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RUNTIME_STATE_UNAVAILABLE));

        org.mockito.Mockito.verify(lossScheduler).schedule(
                org.mockito.ArgumentMatchers.<RunRuntimeLossRequest>argThat(request ->
                        request.userId().equals(userId)
                                && request.remoteSessionId().equals(REMOTE_SESSION_ID)
                                && request.dispatchMessageId().startsWith("msg_")),
                org.mockito.ArgumentMatchers.any(AgentRuntime.class),
                org.mockito.ArgumentMatchers.eq(nodeSnapshot),
                org.mockito.ArgumentMatchers.any(Runnable.class));
        org.mockito.Mockito.verifyNoInteractions(projection);
        assertThat(facade.startRunCommands).isEmpty();
    }

    @Test
    void redisSummaryLeaseRenewalRedisFailureSchedulesLossConvergence() {
        UserId userId = new UserId("usr_lease_renewal_loss");
        Workspace workspaceSnapshot = workspace().withLinuxServerId("server-a", "trace_lease_renewal_loss", NOW);
        ExecutionNode nodeSnapshot = userProcessNode(
                "node_lease_renewal_loss", "http://10.8.0.12:4096");
        Session sessionSnapshot = session().attachOpencodeSession(
                REMOTE_SESSION_ID, nodeSnapshot.executionNodeId(), NOW, "trace_lease_renewal_loss");
        AgentSessionBinding bindingSnapshot = new AgentSessionBinding(
                sessionSnapshot.sessionId(), "opencode", REMOTE_SESSION_ID, nodeSnapshot.executionNodeId(),
                NOW, NOW, "trace_lease_renewal_loss");
        ConversationRunContext context = new ConversationRunContext(
                userId, "opencode", "lease_renewal_loss", "server-a",
                sessionSnapshot, workspaceSnapshot, nodeSnapshot, bindingSnapshot, 1, NOW.plusSeconds(3600));
        StartRunInput input = new StartRunInput(
                sessionSnapshot.sessionId(), "run the tests", List.of(), "msg_lease_renewal_loss",
                null, null, null, null, null, null, "ctx_lease_renewal_loss", "request-lease-renewal-loss");
        ConversationRunContextResolver contextResolver = org.mockito.Mockito.mock(ConversationRunContextResolver.class);
        org.mockito.Mockito.when(contextResolver.resolve(userId, "opencode", input, "trace_lease_renewal_loss"))
                .thenReturn(Optional.of(context));
        RunStorageModeSelector selector = org.mockito.Mockito.mock(RunStorageModeSelector.class);
        org.mockito.Mockito.when(selector.select(userId, input, context)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        AtomicReference<RunRuntimeManifest> runtimeManifest = new AtomicReference<>();
        RunRuntimeStore runtimeStore = mockRuntimeStoreForStart(runtimeManifest);
        RunSummaryPersistencePort summaryPort = org.mockito.Mockito.mock(RunSummaryPersistencePort.class);
        org.mockito.Mockito.when(summaryPort.insertAnchor(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity identity =
                org.mockito.Mockito.mock(com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity.class);
        org.mockito.Mockito.when(identity.linuxServerId()).thenReturn("server-a");
        org.mockito.Mockito.when(identity.backendProcessId()).thenReturn("bjp_lease_renewal_loss");
        AtomicReference<RunOwnerLease> claimedLease = new AtomicReference<>();
        org.mockito.Mockito.when(runtimeStore.claimOwnerLease(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("bjp_lease_renewal_loss")))
                .thenAnswer(invocation -> {
                    RunOwnerLease lease = new RunOwnerLease(
                            invocation.getArgument(0), "bjp_lease_renewal_loss", 7L, NOW.plusSeconds(15));
                    claimedLease.set(lease);
                    return Optional.of(lease);
                });
        java.util.concurrent.atomic.AtomicInteger renewals = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.when(runtimeStore.renewOwnerLease(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    if (renewals.incrementAndGet() == 1) {
                        return Optional.of(invocation.getArgument(0));
                    }
                    throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "redis unavailable");
                });
        RunOwnerLeaseSupervisor supervisor = new RunOwnerLeaseSupervisor(
                runtimeStore, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        RunRuntimeLossConvergenceScheduler lossScheduler =
                org.mockito.Mockito.mock(RunRuntimeLossConvergenceScheduler.class);
        RunTerminalProjectionService projection = org.mockito.Mockito.mock(RunTerminalProjectionService.class);
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = ignored -> Flux.never();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(), new FakeSessionRepository(session()), new FakeRunRepository(),
                new FakeSessionMessageRepository(), new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository(), new RunEventLiveBus(), runtimeStore),
                runtimeRegistry(facade), new FakeAgentSessionBindingRepository(), new RunEventLiveBus(),
                new RunEventPersistencePolicy(), null,
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class),
                ManagedWorkspacePathResolver.legacyOnly(),
                org.mockito.Mockito.mock(RunSessionMessageSnapshotService.class),
                null, null, null, contextResolver, runtimeStore, selector, summaryPort,
                projection, identity, supervisor);
        service.configureRuntimeLossScheduler(lossScheduler);

        Run run = service.startRun(userId, "opencode", input, "trace_lease_renewal_loss");
        supervisor.renewOwnedLeases();

        org.mockito.Mockito.verify(lossScheduler, org.mockito.Mockito.timeout(2_000)).schedule(
                org.mockito.ArgumentMatchers.<RunRuntimeLossRequest>argThat(request ->
                        request.runId().equals(run.runId())
                                && request.remoteSessionId().equals(REMOTE_SESSION_ID)
                                && request.userId().equals(userId)),
                org.mockito.ArgumentMatchers.any(AgentRuntime.class),
                org.mockito.ArgumentMatchers.eq(nodeSnapshot));
        org.mockito.Mockito.verifyNoInteractions(projection);
        assertThat(supervisor.isOwned(run.runId())).isFalse();
        assertThat(claimedLease.get()).isNotNull();
    }

    @Test
    void redisSummaryOwnershipLossDoesNotWriteFailedTerminal() {
        UserId userId = new UserId("usr_owner_loss");
        Workspace workspaceSnapshot = workspace().withLinuxServerId("server-a", "trace_owner_loss", NOW);
        ExecutionNode nodeSnapshot = userProcessNode("node_owner_loss", "http://10.8.0.12:4096");
        Session sessionSnapshot = session().attachOpencodeSession(
                REMOTE_SESSION_ID, nodeSnapshot.executionNodeId(), NOW, "trace_owner_loss");
        AgentSessionBinding bindingSnapshot = new AgentSessionBinding(
                sessionSnapshot.sessionId(), "opencode", REMOTE_SESSION_ID, nodeSnapshot.executionNodeId(),
                NOW, NOW, "trace_owner_loss");
        ConversationRunContext context = new ConversationRunContext(
                userId, "opencode", "owner_loss", "server-a",
                sessionSnapshot, workspaceSnapshot, nodeSnapshot, bindingSnapshot, 1, NOW.plusSeconds(3600));
        StartRunInput input = new StartRunInput(
                sessionSnapshot.sessionId(), "run the tests", List.of(), "msg_owner_loss",
                null, null, null, null, null, null, "ctx_owner_loss", "request-owner-loss");
        ConversationRunContextResolver contextResolver = org.mockito.Mockito.mock(ConversationRunContextResolver.class);
        org.mockito.Mockito.when(contextResolver.resolve(userId, "opencode", input, "trace_owner_loss"))
                .thenReturn(Optional.of(context));
        RunStorageModeSelector selector = org.mockito.Mockito.mock(RunStorageModeSelector.class);
        org.mockito.Mockito.when(selector.select(userId, input, context)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        AtomicReference<RunRuntimeManifest> runtimeManifest = new AtomicReference<>();
        RunRuntimeStore runtimeStore = mockRuntimeStoreForStart(runtimeManifest);
        RunSummaryPersistencePort summaryPort = org.mockito.Mockito.mock(RunSummaryPersistencePort.class);
        org.mockito.Mockito.when(summaryPort.insertAnchor(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        RunTerminalProjectionService projection = org.mockito.Mockito.mock(RunTerminalProjectionService.class);
        com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity identity =
                org.mockito.Mockito.mock(com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity.class);
        org.mockito.Mockito.when(identity.linuxServerId()).thenReturn("server-a");
        org.mockito.Mockito.when(identity.backendProcessId()).thenReturn("bjp_owner_loss");
        RunOwnerLeaseSupervisor supervisor = org.mockito.Mockito.mock(RunOwnerLeaseSupervisor.class);
        RunOwnerLeaseSupervisor.OwnershipHandle handle =
                org.mockito.Mockito.mock(RunOwnerLeaseSupervisor.OwnershipHandle.class);
        org.mockito.Mockito.when(runtimeStore.claimOwnerLease(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("bjp_owner_loss")))
                .thenAnswer(invocation -> Optional.of(new RunOwnerLease(
                        invocation.getArgument(0), "bjp_owner_loss", 3L, NOW.plusSeconds(15))));
        org.mockito.Mockito.when(supervisor.adopt(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(handle));
        org.mockito.Mockito.doThrow(new RunOwnershipLostException("lost"))
                .when(supervisor).requireOwned(handle);
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(), new FakeSessionRepository(session()), new FakeRunRepository(),
                new FakeSessionMessageRepository(), new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository(), new RunEventLiveBus(), runtimeStore),
                runtimeRegistry(facade), new FakeAgentSessionBindingRepository(), new RunEventLiveBus(),
                new RunEventPersistencePolicy(), null,
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class),
                ManagedWorkspacePathResolver.legacyOnly(),
                org.mockito.Mockito.mock(RunSessionMessageSnapshotService.class),
                null, null, null, contextResolver, runtimeStore, selector, summaryPort,
                projection, identity, supervisor);

        assertThatThrownBy(() -> service.startRun(userId, "opencode", input, "trace_owner_loss"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        org.mockito.Mockito.verify(projection, org.mockito.Mockito.never()).project(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(runtimeStore, org.mockito.Mockito.never())
                .appendDurable(org.mockito.ArgumentMatchers.argThat(draft ->
                        draft.type() == RunEventType.RUN_FAILED));
        assertThat(facade.startRunCommands).isEmpty();
    }

    @Test
    void redisSummaryRechecksOwnershipAfterRemoteSessionCreationBeforeBindingWrite() {
        UserId userId = new UserId("usr_binding_fence");
        Workspace workspaceSnapshot = workspace().withLinuxServerId("server-a", "trace_binding_fence", NOW);
        ExecutionNode nodeSnapshot = userProcessNode("node_binding_fence", "http://10.8.0.12:4096");
        Session sessionSnapshot = session();
        ConversationRunContext context = new ConversationRunContext(
                userId, "opencode", "binding_fence", "server-a",
                sessionSnapshot, workspaceSnapshot, nodeSnapshot, null, 1, NOW.plusSeconds(3600));
        StartRunInput input = new StartRunInput(
                sessionSnapshot.sessionId(), "run the tests", List.of(), "msg_binding_fence",
                null, null, null, null, null, null, "ctx_binding_fence", "request-binding-fence");
        ConversationRunContextResolver contextResolver = org.mockito.Mockito.mock(ConversationRunContextResolver.class);
        org.mockito.Mockito.when(contextResolver.resolve(userId, "opencode", input, "trace_binding_fence"))
                .thenReturn(Optional.of(context));
        RunStorageModeSelector selector = org.mockito.Mockito.mock(RunStorageModeSelector.class);
        org.mockito.Mockito.when(selector.select(userId, input, context)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        AtomicReference<RunRuntimeManifest> runtimeManifest = new AtomicReference<>();
        RunRuntimeStore runtimeStore = mockRuntimeStoreForStart(runtimeManifest);
        RunSummaryPersistencePort summaryPort = org.mockito.Mockito.mock(RunSummaryPersistencePort.class);
        org.mockito.Mockito.when(summaryPort.insertAnchor(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity identity =
                org.mockito.Mockito.mock(com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity.class);
        org.mockito.Mockito.when(identity.linuxServerId()).thenReturn("server-a");
        org.mockito.Mockito.when(identity.backendProcessId()).thenReturn("bjp_binding_fence");
        RunOwnerLeaseSupervisor supervisor = org.mockito.Mockito.mock(RunOwnerLeaseSupervisor.class);
        RunOwnerLeaseSupervisor.OwnershipHandle handle =
                org.mockito.Mockito.mock(RunOwnerLeaseSupervisor.OwnershipHandle.class);
        RunOwnerLease lease = new RunOwnerLease(
                new RunId("run_binding_placeholder"), "bjp_binding_fence", 4L, NOW.plusSeconds(15));
        org.mockito.Mockito.when(runtimeStore.claimOwnerLease(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("bjp_binding_fence")))
                .thenAnswer(invocation -> Optional.of(new RunOwnerLease(
                        invocation.getArgument(0), lease.ownerBackendProcessId(), lease.fencingToken(), lease.expiresAt())));
        org.mockito.Mockito.when(supervisor.adopt(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(handle));
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(ignored -> {
            if (checks.incrementAndGet() == 2) {
                throw new RunOwnershipLostException("lost after create");
            }
            return null;
        }).when(supervisor).requireOwned(handle);
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(), new FakeSessionRepository(session()), new FakeRunRepository(),
                new FakeSessionMessageRepository(), new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository(), new RunEventLiveBus(), runtimeStore),
                runtimeRegistry(facade), new FakeAgentSessionBindingRepository(), new RunEventLiveBus(),
                new RunEventPersistencePolicy(), null,
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class),
                ManagedWorkspacePathResolver.legacyOnly(),
                org.mockito.Mockito.mock(RunSessionMessageSnapshotService.class),
                null, null, null, contextResolver, runtimeStore, selector, summaryPort,
                org.mockito.Mockito.mock(RunTerminalProjectionService.class), identity, supervisor);

        assertThatThrownBy(() -> service.startRun(userId, "opencode", input, "trace_binding_fence"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        assertThat(facade.createSessionCommands).hasSize(1);
        assertThat(facade.startRunCommands).isEmpty();
        org.mockito.Mockito.verify(summaryPort, org.mockito.Mockito.never())
                .persistInitialAgentBinding(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void redisSummaryAnchorConflictDiscardsUnanchoredRuntimeBeforeReturningExistingRun() {
        UserId userId = new UserId("usr_anchor_conflict");
        Workspace workspaceSnapshot = workspace().withLinuxServerId("server-a", "trace_anchor_conflict", NOW);
        ExecutionNode nodeSnapshot = userProcessNode(
                "node_ocp_anchor_conflict", "http://10.8.0.12:4096");
        Session sessionSnapshot = session().attachOpencodeSession(
                REMOTE_SESSION_ID, nodeSnapshot.executionNodeId(), NOW, "trace_anchor_conflict");
        AgentSessionBinding bindingSnapshot = new AgentSessionBinding(
                sessionSnapshot.sessionId(), "opencode", REMOTE_SESSION_ID, nodeSnapshot.executionNodeId(),
                NOW, NOW, "trace_anchor_conflict");
        ConversationRunContext context = new ConversationRunContext(
                userId, "opencode", "ocp_anchor_conflict", "server-a",
                sessionSnapshot, workspaceSnapshot, nodeSnapshot, bindingSnapshot, 1, NOW.plusSeconds(3600));
        StartRunInput input = new StartRunInput(
                sessionSnapshot.sessionId(), "run the tests", List.of(), "msg_anchor_conflict",
                null, null, null, null, null, null, "ctx_anchor_conflict", "request-anchor-conflict");
        ConversationRunContextResolver contextResolver = org.mockito.Mockito.mock(ConversationRunContextResolver.class);
        org.mockito.Mockito.when(contextResolver.resolve(userId, "opencode", input, "trace_anchor_conflict"))
                .thenReturn(Optional.of(context));
        RunStorageModeSelector selector = org.mockito.Mockito.mock(RunStorageModeSelector.class);
        org.mockito.Mockito.when(selector.select(userId, input, context)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        AtomicReference<RunRuntimeManifest> runtimeManifest = new AtomicReference<>();
        RunRuntimeStore runtimeStore = mockRuntimeStoreForStart(runtimeManifest);
        RunSummaryPersistencePort summaryPort = org.mockito.Mockito.mock(RunSummaryPersistencePort.class);
        AtomicReference<com.icbc.testagent.domain.run.RunPersistenceAnchor> attemptedAnchor = new AtomicReference<>();
        org.mockito.Mockito.when(summaryPort.insertAnchor(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            attemptedAnchor.set(invocation.getArgument(0));
            return false;
        });
        org.mockito.Mockito.when(summaryPort.findBySessionAndClientRequestId(
                        sessionSnapshot.sessionId(), "request-anchor-conflict"))
                .thenAnswer(ignored -> Optional.of(attemptedAnchor.get()));
        com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity identity =
                org.mockito.Mockito.mock(com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity.class);
        org.mockito.Mockito.when(identity.linuxServerId()).thenReturn("server-a");
        org.mockito.Mockito.when(identity.backendProcessId()).thenReturn("bjp_summary_backend");
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(), new FakeSessionRepository(session()), new FakeRunRepository(),
                new FakeSessionMessageRepository(), new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository(), new RunEventLiveBus(), runtimeStore),
                runtimeRegistry(facade), new FakeAgentSessionBindingRepository(), new RunEventLiveBus(),
                new RunEventPersistencePolicy(), null,
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class),
                ManagedWorkspacePathResolver.legacyOnly(),
                org.mockito.Mockito.mock(RunSessionMessageSnapshotService.class),
                null, null, null, contextResolver, runtimeStore, selector, summaryPort,
                org.mockito.Mockito.mock(RunTerminalProjectionService.class), identity,
                org.mockito.Mockito.mock(RunOwnerLeaseSupervisor.class));

        Run result = service.startRun(userId, "opencode", input, "trace_anchor_conflict");

        assertThat(result.runId()).isEqualTo(attemptedAnchor.get().runId());
        org.mockito.Mockito.verify(runtimeStore).discardBeforeDispatch(attemptedAnchor.get().runId());
        org.mockito.Mockito.verify(runtimeStore).confirmClientRequest(
                sessionSnapshot.sessionId(), "request-anchor-conflict", attemptedAnchor.get().runId());
        assertThat(facade.createSessionCommands).isEmpty();
        assertThat(facade.startRunCommands).isEmpty();
    }

    @Test
    void userAwareRunRebuildsRemoteSessionWhenExistingBindingPointsToDifferentNode() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeAgentSessionBindingRepository bindings = new FakeAgentSessionBindingRepository();
        bindings.save(new AgentSessionBinding(
                new SessionId("ses_1234567890abcdef"),
                "opencode",
                "ses_oldremote1234567890abcdef",
                new ExecutionNodeId("node_old1234567890abcdef"),
                NOW,
                NOW,
                "trace_1234567890abcdef"));
        UserOpencodeProcessAssignmentService assignmentService = org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        ExecutionNode assignedNode = userProcessNode("node_ocp_1234567890abcdef", "http://10.8.0.12:4096");
        org.mockito.Mockito.when(assignmentService.requireReadyProcess(
                        new UserId("usr_1234567890abcdef"),
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(assignedNode));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                bindings,
                assignmentService);

        Run run = service.startRun(
                new UserId("usr_1234567890abcdef"),
                new StartRunInput(new SessionId("ses_1234567890abcdef"), "run the tests", List.of(), null, null, null, null, null),
                "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(facade.createSessionCommands).hasSize(1);
        assertThat(facade.createSessionCommands.getFirst().node().executionNodeId()).isEqualTo(assignedNode.executionNodeId());
        assertThat(facade.createSessionCommands.getFirst().title()).isNull();
        assertThat(facade.startRunCommands.getFirst().opencodeSessionId()).isEqualTo(REMOTE_SESSION_ID);
        assertThat(bindings.findBySessionIdAndAgentId(new SessionId("ses_1234567890abcdef"), "opencode"))
                .get()
                .satisfies(binding -> {
                    assertThat(binding.remoteSessionId()).isEqualTo(REMOTE_SESSION_ID);
                    assertThat(binding.executionNodeId()).isEqualTo(assignedNode.executionNodeId());
                });
    }

    @Test
    void userAwareRunDoesNotCreateLocalRunWhenUserProcessIsUnavailable() {
        FakeRunRepository runs = new FakeRunRepository();
        UserOpencodeProcessAssignmentService assignmentService = org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        org.mockito.Mockito.when(assignmentService.requireReadyProcess(
                        new UserId("usr_1234567890abcdef"),
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenThrow(new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "请先初始化 opencode 进程"));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository()),
                runtimeRegistry(new FakeOpencodeFacade()),
                new FakeAgentSessionBindingRepository(),
                assignmentService);

        assertThatThrownBy(() -> service.startRun(
                        new UserId("usr_1234567890abcdef"),
                        new StartRunInput(new SessionId("ses_1234567890abcdef"), "run the tests", List.of(), null, null, null, null, null),
                        "trace_1234567890abcdef"))
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
        assertThat(runs.saved).isEmpty();
    }

    @Test
    void internalModelSourceRejectsAnonymousRunBeforeLegacyRouting() {
        FakeRunRepository runs = new FakeRunRepository();
        ModelCatalogApplicationService modelCatalog = org.mockito.Mockito.mock(ModelCatalogApplicationService.class);
        org.mockito.Mockito.when(modelCatalog.internalSourceEnabled()).thenReturn(true);
        RunApplicationService service = serviceWithModelCatalog(
                new FakeOpencodeFacade(),
                runs,
                new FakeRunEventRepository(),
                modelCatalog);

        Run run = service.startRun(
                new StartRunInput(new SessionId("ses_1234567890abcdef"), "run the tests", List.of(), null, null, null, null, null),
                "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(runs.saved).isNotEmpty();
    }

    @Test
    void internalModelSourceSyncsProviderConfigWithCurrentUserId() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        UserId userId = new UserId("usr_1234567890abcdef");
        ExecutionNode assignedNode = userProcessNode("node_ocp_1234567890abcdef", "http://10.8.0.12:4096");
        UserOpencodeProcessAssignmentService assignmentService = org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        org.mockito.Mockito.when(assignmentService.requireReadyProcess(userId, "opencode", "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(assignedNode));
        ModelCatalogApplicationService modelCatalog = org.mockito.Mockito.mock(ModelCatalogApplicationService.class);
        org.mockito.Mockito.when(modelCatalog.managedSourceEnabled()).thenReturn(true);
        org.mockito.Mockito.when(modelCatalog.internalSourceEnabled()).thenReturn(true);
        org.mockito.Mockito.when(modelCatalog.listModels()).thenReturn(List.of(modelPayload("icbc-openai", "DeepSeek-V4-Flash-W8A8", true)));
        RunApplicationService service = serviceWithModelCatalogAndAssignment(facade, modelCatalog, assignmentService);

        Run run = service.startRun(
                userId,
                new StartRunInput(new SessionId("ses_1234567890abcdef"), "run the tests", List.of(), null, null, null, null, null),
                "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        org.mockito.Mockito.verify(modelCatalog, org.mockito.Mockito.never()).syncProviderConfig(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(assignedNode),
                org.mockito.ArgumentMatchers.eq("trace_1234567890abcdef"),
                org.mockito.ArgumentMatchers.eq(userId));
    }

    @Test
    void servicePassesPromptPartsAndRuntimeSelectionToOpencodeFacade() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionMessageRepository messages = new FakeSessionMessageRepository();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                messages,
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(new StartRunInput(
                        new SessionId("ses_1234567890abcdef"),
                        null,
                        List.of(
                                StartRunInput.PromptPart.text("review this file"),
                                new StartRunInput.PromptPart(
                                        "file",
                                        null,
                                        "src/App.tsx",
                                        "App.tsx",
                                        "text/plain",
                                        "export function App() { return null; }",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        Map.<String, Object>of(
                                                "contextType", "selection",
                                                "startLine", 20,
                                                "endLine", 35,
                                                "start", 0,
                                                "end", 37),
                                        null),
                                StartRunInput.PromptPart.agent("build", "Build")),
                        "msg_remote1234567890abcdef",
                        "build",
                        "anthropic/claude-sonnet-4-5",
                        "default",
                        "build"),
                "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(facade.startRunCommands).hasSize(1);
        OpencodeStartRunCommand command = facade.startRunCommands.getFirst();
        assertThat(command.prompt()).isEqualTo("review this file");
        assertThat(command.messageId()).isEqualTo("msg_remote1234567890abcdef");
        assertThat(command.agent()).isEqualTo("build");
        assertThat(command.modelProviderId()).isEqualTo("anthropic");
        assertThat(command.modelId()).isEqualTo("claude-sonnet-4-5");
        assertThat(command.variant()).isEqualTo("default");
        assertThat(command.parts()).hasSize(3);
        assertThat(command.parts().get(0).type()).isEqualTo("text");
        assertThat(command.parts().get(1).type()).isEqualTo("file");
        assertThat(command.parts().get(1).url()).startsWith("data:text/plain");
        assertThat(command.parts().get(1).source()).containsEntry("contextType", "selection");
        assertThat(command.parts().get(1).source()).containsEntry("startLine", 20);
        assertThat(command.parts().get(1).source()).containsEntry("endLine", 35);
        assertThat(command.parts().get(2).type()).isEqualTo("agent");
        assertThat(command.parts().get(2).name()).isEqualTo("Build");
        assertThat(messages.saved).hasSize(1);
        assertThat(messages.saved.getFirst()).satisfies(userMessage -> {
            assertThat(userMessage.role()).isEqualTo(com.icbc.testagent.domain.session.SessionMessageRole.USER);
            assertThat(userMessage.partsJson()).contains("\"type\":\"file\"");
            assertThat(userMessage.partsJson()).contains("\"path\":\"src/App.tsx\"");
            assertThat(userMessage.partsJson()).contains("\"contextType\":\"selection\"");
            assertThat(userMessage.partsJson()).contains("\"startLine\":20");
            assertThat(userMessage.partsJson()).contains("\"endLine\":35");
        });
    }

    @Test
    void managedCatalogFallsBackStaleModelToDefaultModel() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        ModelCatalogApplicationService modelCatalog = managedModelCatalog(List.of(
                modelPayload("icbc-openai", "DeepSeek-V4-Flash-W8A8", true),
                modelPayload("icbc-openai", "Qwen3.6-27B", false)));
        RunApplicationService service = serviceWithModelCatalog(facade, runs, events, modelCatalog);

        service.startRun(new StartRunInput(
                        new SessionId("ses_1234567890abcdef"),
                        "run with stale model",
                        List.of(),
                        null,
                        "build",
                        "opencode-zen/north-mini-code",
                        null,
                        null),
                "trace_1234567890abcdef");

        assertThat(facade.startRunCommands).hasSize(1);
        OpencodeStartRunCommand command = facade.startRunCommands.getFirst();
        assertThat(command.modelProviderId()).isEqualTo("opencode-zen");
        assertThat(command.modelId()).isEqualTo("north-mini-code");
    }

    @Test
    void managedCatalogPreservesAvailableModelSelection() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = serviceWithModelCatalog(
                facade,
                runs,
                events,
                managedModelCatalog(List.of(
                        modelPayload("icbc-openai", "DeepSeek-V4-Flash-W8A8", true),
                        modelPayload("icbc-openai", "Qwen3.6-27B", false))));

        startRunWithModel(service, "icbc-openai/Qwen3.6-27B");

        assertThat(facade.startRunCommands).hasSize(1);
        OpencodeStartRunCommand command = facade.startRunCommands.getFirst();
        assertThat(command.modelProviderId()).isEqualTo("icbc-openai");
        assertThat(command.modelId()).isEqualTo("Qwen3.6-27B");
    }

    @Test
    void managedCatalogFallsBackInvalidModelFormatToDefaultModel() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = serviceWithModelCatalog(
                facade,
                runs,
                events,
                managedModelCatalog(List.of(modelPayload("icbc-openai", "DeepSeek-V4-Flash-W8A8", true))));

        startRunWithModel(service, "north-mini-code");

        assertThat(facade.startRunCommands).hasSize(1);
        OpencodeStartRunCommand command = facade.startRunCommands.getFirst();
        assertThat(command.modelProviderId()).isNull();
        assertThat(command.modelId()).isNull();
    }

    @Test
    void managedCatalogFallsBackMissingModelToDefaultModel() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = serviceWithModelCatalog(
                facade,
                runs,
                events,
                managedModelCatalog(List.of(modelPayload("icbc-openai", "DeepSeek-V4-Flash-W8A8", true))));

        startRunWithModel(service, null);

        assertThat(facade.startRunCommands).hasSize(1);
        OpencodeStartRunCommand command = facade.startRunCommands.getFirst();
        assertThat(command.modelProviderId()).isNull();
        assertThat(command.modelId()).isNull();
    }

    @Test
    void managedCatalogRejectsRunWhenNoModelIsAvailable() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = serviceWithModelCatalog(facade, runs, events, managedModelCatalog(List.of()));

        service.startRun(new StartRunInput(
                        new SessionId("ses_1234567890abcdef"),
                        "run without models",
                        List.of(),
                        null,
                        "build",
                        "opencode-zen/north-mini-code",
                        null,
                        null),
                "trace_1234567890abcdef");

        assertThat(facade.startRunCommands).hasSize(1);
        assertThat(runs.saved).isNotEmpty();
    }

    @Test
    void servicePreservesLegacyPromptWhenPartsDoNotIncludeText() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository()),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        service.startRun(new StartRunInput(
                        new SessionId("ses_1234567890abcdef"),
                        "legacy prompt",
                        List.of(StartRunInput.PromptPart.file(
                                "src/App.tsx",
                                "App.tsx",
                                "text/plain",
                                null,
                                "export const value = 1;",
                                Map.of())),
                        null,
                        null,
                        null,
                        null,
                        null),
                "trace_1234567890abcdef");

        assertThat(facade.startRunCommands.getFirst().parts()).extracting(OpencodePromptPart::type)
                .containsExactly("text", "file");
        assertThat(facade.startRunCommands.getFirst().parts().getFirst().text()).isEqualTo("legacy prompt");
    }

    @Test
    void serviceReusesMappedOpencodeSessionAndStickyNodeOnLaterRun() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        Session mappedSession = session().attachOpencodeSession(
                REMOTE_SESSION_ID,
                node().executionNodeId(),
                NOW.plusSeconds(1),
                "trace_1234567890abcdef");
        FakeRoutingDecisionRepository routingDecisions = new FakeRoutingDecisionRepository();
        FakeExecutionNodeRepository nodes = new FakeExecutionNodeRepository();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(mappedSession),
                runs,
                new FakeSessionMessageRepository(),
                nodes,
                routingDecisions,
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run again", "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(facade.createSessionCommands).isEmpty();
        assertThat(nodes.findRoutableNodesCalls).isZero();
        assertThat(routingDecisions.saved).hasSize(1);
        assertThat(routingDecisions.saved.getFirst().executionNodeId()).isEqualTo(node().executionNodeId());
        assertThat(facade.startRunCommands).hasSize(1);
        assertThat(facade.startRunCommands.getFirst().opencodeSessionId()).isEqualTo(REMOTE_SESSION_ID);
        assertThat(facade.startRunCommands.getFirst().workspace()).isNull();
    }

    @Test
    void serviceMarksRunSucceededWhenOpencodeStreamEmitsTerminalEvent() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.RUN_SUCCEEDED,
                command.traceId(),
                Instant.now(),
                Map.of("messageID", "msg_remote1234567890abcdef")));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        awaitRunStatus(service, run.runId(), RunStatus.SUCCEEDED);
        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.RUN_SUCCEEDED);
    }

    @Test
    void serviceRegistersOnlyFirstRunTitleWatchAndClosesOlderTitleWaitBeforeStartingNextRun() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunSessionTitleWatchService titleWatchService = org.mockito.Mockito.mock(RunSessionTitleWatchService.class);
        Session initialTitleSession = session().updateTitleAndPinned(
                OpencodeSessionTitlePolicy.initialPlatformTitle("run the tests"),
                false,
                NOW,
                "trace_1234567890abcdef");
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(initialTitleSession),
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository()),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                null,
                ManagedWorkspacePathResolver.legacyOnly(),
                null,
                null,
                null,
                null,
                titleWatchService);

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        org.mockito.Mockito.verify(titleWatchService).closeTitleWaitForNextRun(
                org.mockito.ArgumentMatchers.eq(new SessionId("ses_1234567890abcdef")),
                org.mockito.ArgumentMatchers.eq("trace_1234567890abcdef"));
        org.mockito.Mockito.verify(titleWatchService).registerFirstRun(
                org.mockito.ArgumentMatchers.eq(new SessionId("ses_1234567890abcdef")),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(REMOTE_SESSION_ID),
                org.mockito.ArgumentMatchers.eq(OpencodeSessionTitlePolicy.initialPlatformTitle("run the tests")));
    }

    @Test
    void serviceSynchronizesRootSessionTitleFromSessionUpdatedEvent() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.SESSION_UPDATED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "rawType", "session.updated",
                        "sessionID", REMOTE_SESSION_ID,
                        "info", Map.of("title", "  AI 生成的会话标题  "))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                null,
                ManagedWorkspacePathResolver.legacyOnly(),
                null,
                new FakeRunSessionScopeRepository(),
                null,
                null);

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.SESSION_UPDATED);
        assertThat(sessions.current.title()).isEqualTo("  AI 生成的会话标题  ");
        assertThat(events.events.get(2).payload())
                .containsEntry("rootSessionId", REMOTE_SESSION_ID)
                .containsEntry("sessionId", REMOTE_SESSION_ID)
                .containsEntry("isChildSession", false)
                .containsEntry("platformSessionTitleSynchronized", true)
                .containsEntry("platformSessionTitle", "AI 生成的会话标题");
    }

    @Test
    void servicePersistsSessionUpdatedWhenTitleSynchronizationFails() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        sessions.failTitleUpdate = true;
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.SESSION_UPDATED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "rawType", "session.updated",
                        "sessionID", REMOTE_SESSION_ID,
                        "info", Map.of("title", "AI 标题保存失败"))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.SESSION_UPDATED);
        assertThat(sessions.current.title()).isEqualTo("Demo session");
        assertThat(events.events.get(2).payload())
                .doesNotContainKeys("platformSessionTitleSynchronized", "platformSessionTitle");
    }

    @Test
    void serviceDoesNotSynchronizeRootTitleWhenPlatformSessionRemoteBindingDiffers() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        sessions.remoteSessionIdOverride = "ses_mismatched1234567890abcdef";
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.SESSION_UPDATED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "rawType", "session.updated",
                        "sessionID", REMOTE_SESSION_ID,
                        "info", Map.of("title", "不应覆盖的平台标题"))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.SESSION_UPDATED);
        assertThat(sessions.current.title()).isEqualTo("Demo session");
        assertThat(events.events.get(2).payload())
                .doesNotContainKeys("platformSessionTitleSynchronized", "platformSessionTitle");
    }

    @Test
    void serviceSynchronizesRootSessionTitleFromWrappedSessionUpdatedEvent() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.SESSION_UPDATED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "rawType", "session.updated",
                        "rawPayload", Map.of("properties", Map.of(
                                "sessionID", REMOTE_SESSION_ID,
                                "info", Map.of("title", "  兼容事件标题  "))))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                null,
                ManagedWorkspacePathResolver.legacyOnly(),
                null,
                new FakeRunSessionScopeRepository(),
                null,
                null);

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.SESSION_UPDATED);
        assertThat(sessions.current.title()).isEqualTo("  兼容事件标题  ");
        assertThat(events.events.get(2).payload())
                .containsEntry("platformSessionTitleSynchronized", true)
                .containsEntry("platformSessionTitle", "兼容事件标题")
                .doesNotContainKey("rawPayload");
    }

    @Test
    void serviceDoesNotSynchronizeOpenCodeDefaultSessionTitle() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.SESSION_UPDATED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "rawType", "session.updated",
                        "sessionID", REMOTE_SESSION_ID,
                        "info", Map.of("title", "New session - 2026-07-10T04:56:46.518Z"))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.SESSION_UPDATED);
        assertThat(sessions.current.title()).isEqualTo("Demo session");
        assertThat(events.events.get(2).payload())
                .doesNotContainKeys("platformSessionTitleSynchronized", "platformSessionTitle");
    }

    @Test
    void serviceDoesNotSynchronizeDiscoveredChildSessionTitleToPlatformRootSession() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        facade.streamEvents = command -> Flux.just(
                new RunEventDraft(
                        command.runId(),
                        RunEventType.SESSION_CREATED,
                        command.traceId(),
                        Instant.now(),
                        Map.of(
                                "rawType", "session.created",
                                "sessionID", "ses_child1234567890abcdef",
                                "parentID", REMOTE_SESSION_ID)),
                new RunEventDraft(
                        command.runId(),
                        RunEventType.SESSION_UPDATED,
                        command.traceId(),
                        Instant.now(),
                        Map.of(
                                "rawType", "session.updated",
                                "sessionID", "ses_child1234567890abcdef",
                                "parentID", REMOTE_SESSION_ID,
                                "info", Map.of("title", "子智能体标题"))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                null,
                ManagedWorkspacePathResolver.legacyOnly(),
                null,
                new FakeRunSessionScopeRepository(),
                null,
                null);

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitEventTypes(
                events,
                RunEventType.RUN_CREATED,
                RunEventType.RUN_STARTED,
                RunEventType.SESSION_CHILD_DISCOVERED,
                RunEventType.SESSION_SCOPE_UPDATED,
                RunEventType.SESSION_CREATED,
                RunEventType.SESSION_UPDATED);
        assertThat(sessions.current.title()).isEqualTo("Demo session");
        assertThat(events.events.get(5).payload())
                .doesNotContainKeys("platformSessionTitleSynchronized", "platformSessionTitle");
    }

    @Test
    void serviceDoesNotSynchronizeUnknownSessionTitleToPlatformRootSession() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.SESSION_UPDATED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "rawType", "session.updated",
                        "sessionID", "ses_unknown1234567890abcdef",
                        "info", Map.of("title", "未知会话标题"))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED);
        assertThat(sessions.current.title()).isEqualTo("Demo session");
    }

    @Test
    void serviceDoesNotSynchronizeBlankRootSessionTitle() {
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.SESSION_UPDATED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "rawType", "session.updated",
                        "sessionID", REMOTE_SESSION_ID,
                        "info", Map.of("title", "   "))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.SESSION_UPDATED);
        assertThat(sessions.current.title()).isEqualTo("Demo session");
        assertThat(events.events.get(2).payload())
                .doesNotContainKeys("platformSessionTitleSynchronized", "platformSessionTitle");
    }

    @Test
    void serviceKeepsSucceededRunWhenAsyncPromptTransportFailsAfterTerminalEvent() {
        TerminalRaceRunRepository runs = new TerminalRaceRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        FakeSessionMessageRepository messages = new FakeSessionMessageRepository();
        FakeExecutionNodeRepository nodes = new FakeExecutionNodeRepository();
        FakeAgentSessionBindingRepository bindings = new FakeAgentSessionBindingRepository();
        AgentRuntimeRegistry registry = runtimeRegistry(facade);
        CountingSnapshotService snapshots = new CountingSnapshotService(runs, sessions, messages, nodes, registry, bindings);
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.RUN_SUCCEEDED,
                command.traceId(),
                Instant.now(),
                Map.of("messageID", "msg_remote1234567890abcdef")))
                .delaySubscription(Duration.ofMillis(50));
        facade.startRun = ignored -> Mono.error(new IllegalStateException("Streaming response failed"));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                runs,
                messages,
                nodes,
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                registry,
                bindings,
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                null,
                com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver.legacyOnly(),
                snapshots,
                null,
                null);

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        awaitRunStatus(service, run.runId(), RunStatus.SUCCEEDED);
        sleep(Duration.ofMillis(200));
        assertThat(service.getRun(run.runId()).status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(events.events).extracting(RunEvent::type)
                .containsExactly(RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.RUN_SUCCEEDED);
        assertThat(snapshots.persistCalls).isEqualTo(1);
    }

    @Test
    void serviceUsesLaterRootTerminalEventAfterEarlierAsyncPromptTransportFailure() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        FakeSessionMessageRepository messages = new FakeSessionMessageRepository();
        FakeExecutionNodeRepository nodes = new FakeExecutionNodeRepository();
        FakeAgentSessionBindingRepository bindings = new FakeAgentSessionBindingRepository();
        AgentRuntimeRegistry registry = runtimeRegistry(facade);
        CountingSnapshotService snapshots = new CountingSnapshotService(runs, sessions, messages, nodes, registry, bindings);
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.RUN_SUCCEEDED,
                command.traceId(),
                Instant.now().plusMillis(100),
                Map.of("messageID", "msg_remote1234567890abcdef")))
                .delaySubscription(Duration.ofMillis(80));
        facade.startRun = ignored -> Mono.error(new IllegalStateException("Streaming response failed"));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                runs,
                messages,
                nodes,
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                registry,
                bindings,
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                null,
                com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver.legacyOnly(),
                snapshots,
                null,
                null);

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        awaitRunStatus(service, run.runId(), RunStatus.SUCCEEDED);
        sleep(Duration.ofMillis(500));
        assertThat(service.getRun(run.runId()).status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(events.events).extracting(RunEvent::type)
                .containsExactly(RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.RUN_SUCCEEDED);
        assertThat(snapshots.persistCalls).isEqualTo(1);
    }

    @Test
    void serviceMarksRunFailedWhenStreamingTransportFailsWithoutTerminalEvent() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.startRun = ignored -> Mono.error(new IllegalStateException("Streaming response failed"));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        awaitRunStatus(service, run.runId(), RunStatus.FAILED);
        assertThat(events.events).extracting(RunEvent::type)
                .containsExactly(RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.RUN_FAILED);
    }

    @Test
    void serviceDoesNotMarkRunSucceededWhenChildSessionIdleIsMisderivedByClient() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(
                new RunEventDraft(
                        command.runId(),
                        RunEventType.SESSION_STATUS,
                        command.traceId(),
                        Instant.now(),
                        Map.of(
                                "rawType", "session.status",
                                "sessionID", "ses_child1234567890abcdef",
                                "parentID", REMOTE_SESSION_ID,
                                "status", Map.of("type", "idle"))),
                new RunEventDraft(
                        command.runId(),
                        RunEventType.RUN_SUCCEEDED,
                        command.traceId(),
                        Instant.now(),
                        Map.of(
                                "rawType", "session.status",
                                "sessionID", "ses_child1234567890abcdef",
                                "parentID", REMOTE_SESSION_ID,
                                "status", Map.of("type", "idle"))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitEventTypes(
                events,
                RunEventType.RUN_CREATED,
                RunEventType.RUN_STARTED,
                RunEventType.SESSION_CHILD_DISCOVERED,
                RunEventType.SESSION_SCOPE_UPDATED,
                RunEventType.SESSION_STATUS);
        assertThat(service.getRun(run.runId()).status()).isEqualTo(RunStatus.RUNNING);
    }

    @Test
    void servicePersistsAssistantSnapshotAndRunUsageWhenTerminalEventArrives() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeSessionMessageRepository messages = new FakeSessionMessageRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.sessionMessagesResult = new OpencodeSessionMessagesResult(
                List.of(
                        new OpencodeSessionMessage(
                                Map.of(
                                        "id", "msg_remote_tools_1234567890abcdef",
                                        "type", "assistant",
                                        "role", "assistant"),
                                List.of(
                                        Map.of(
                                                "id", "part_reasoning",
                                                "messageID", "msg_remote_tools_1234567890abcdef",
                                                "type", "reasoning",
                                                "text", "内部推理不应进入回答"),
                                        Map.of(
                                                "id", "part_tool",
                                                "messageID", "msg_remote_tools_1234567890abcdef",
                                                "type", "tool",
                                                "tool", "bash",
                                                "state", Map.of(
                                                        "status", "completed",
                                                        "output", "line 1\n\n\nline 2")))),
                        new OpencodeSessionMessage(
                                Map.of(
                                        "id", "msg_remote_1234567890abcdef",
                                        "type", "assistant",
                                        "role", "assistant",
                                        "cost", new BigDecimal("0.25000000"),
                                        "tokens", Map.of(
                                                "input", 11,
                                                "output", 12,
                                                "reasoning", 3,
                                                "cache", Map.of("read", 4, "write", 5))),
                                List.of(Map.of(
                                        "id", "part_1",
                                        "messageID", "msg_remote_1234567890abcdef",
                                        "type", "text",
                                        "text", "assistant answer")))),
                null,
                null);
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.RUN_SUCCEEDED,
                command.traceId(),
                Instant.now(),
                Map.of("messageID", "msg_remote_1234567890abcdef")));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                messages,
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitRunStatus(service, run.runId(), RunStatus.SUCCEEDED);
        awaitMessageCount(messages, 3);
        TokenUsage expectedUsage = new TokenUsage(11L, 12L, 3L, 4L, 5L);
        assertThat(service.getRun(run.runId()).tokenUsage()).isEqualTo(expectedUsage);
        assertThat(service.getRun(run.runId()).costUsd()).isEqualByComparingTo("0.25000000");
        assertThat(messages.saved.get(0)).satisfies(user -> {
            assertThat(user.role()).isEqualTo(com.icbc.testagent.domain.session.SessionMessageRole.USER);
            assertThat(user.runId()).isEqualTo(run.runId());
        });
        assertThat(messages.saved.get(1)).satisfies(toolSnapshot -> {
            assertThat(toolSnapshot.role()).isEqualTo(com.icbc.testagent.domain.session.SessionMessageRole.ASSISTANT);
            assertThat(toolSnapshot.content()).isEmpty();
            assertThat(toolSnapshot.partsJson()).contains("\"part_tool\"");
            assertThat(toolSnapshot.partsJson()).contains("line 1\\n\\n\\nline 2");
        });
        assertThat(messages.saved.get(2)).satisfies(assistant -> {
            assertThat(assistant.role()).isEqualTo(com.icbc.testagent.domain.session.SessionMessageRole.ASSISTANT);
            assertThat(assistant.runId()).isEqualTo(run.runId());
            assertThat(assistant.agentId()).isEqualTo("opencode");
            assertThat(assistant.remoteMessageId()).isEqualTo("msg_remote_1234567890abcdef");
            assertThat(assistant.content()).isEqualTo("assistant answer");
            assertThat(assistant.partsJson()).contains("\"part_1\"");
            assertThat(assistant.tokenUsage()).isEqualTo(expectedUsage);
            assertThat(assistant.costUsd()).isEqualByComparingTo("0.25000000");
        });
    }

    @Test
    void servicePagesAssistantSnapshotWhenRemoteReturnsNextCursor() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeSessionMessageRepository messages = new FakeSessionMessageRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.sessionMessages = command -> {
            if (command.cursor() == null) {
                return Mono.just(new OpencodeSessionMessagesResult(
                        List.of(new OpencodeSessionMessage(
                                Map.of(
                                        "id", "msg_page_one_1234567890abcdef",
                                        "type", "assistant",
                                        "role", "assistant",
                                        "time", Map.of("created", 1781846402000L),
                                        "tokens", Map.of("output", 20),
                                        "cost", new BigDecimal("2.00000000")),
                                List.of(Map.of(
                                        "id", "part_page_one",
                                        "messageID", "msg_page_one_1234567890abcdef",
                                        "type", "text",
                                        "text", "第一页")))),
                        null,
                        "cursor_page_two"));
            }
            return Mono.just(new OpencodeSessionMessagesResult(
                    List.of(new OpencodeSessionMessage(
                            Map.of(
                                    "id", "msg_page_two_1234567890abcdef",
                                    "type", "assistant",
                                    "role", "assistant",
                                    "time", Map.of("created", 1781846401000L),
                                    "tokens", Map.of("output", 10),
                                    "cost", new BigDecimal("1.00000000")),
                            List.of(Map.of(
                                    "id", "part_page_two",
                                    "messageID", "msg_page_two_1234567890abcdef",
                                    "type", "text",
                                    "text", "第二页")))),
                    null,
                    null));
        };
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.RUN_SUCCEEDED,
                command.traceId(),
                Instant.now(),
                Map.of("messageID", "msg_page_two_1234567890abcdef")));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                messages,
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitRunStatus(service, run.runId(), RunStatus.SUCCEEDED);
        awaitMessageCount(messages, 3);
        assertThat(facade.sessionMessagesCommands).hasSize(2);
        assertThat(facade.sessionMessagesCommands.get(0).limit()).isEqualTo(50);
        assertThat(facade.sessionMessagesCommands.get(0).cursor()).isNull();
        assertThat(facade.sessionMessagesCommands.get(1).limit()).isEqualTo(50);
        assertThat(facade.sessionMessagesCommands.get(1).cursor()).isEqualTo("cursor_page_two");
        assertThat(messages.saved).extracting(SessionMessage::content).contains("第一页", "第二页");
        assertThat(messages.saved).filteredOn(message -> "第一页".equals(message.content()))
                .singleElement()
                .satisfies(message -> assertThat(message.createdAt()).isEqualTo(Instant.ofEpochMilli(1781846402000L)));
        assertThat(messages.saved).filteredOn(message -> "第二页".equals(message.content()))
                .singleElement()
                .satisfies(message -> assertThat(message.createdAt()).isEqualTo(Instant.ofEpochMilli(1781846401000L)));
        assertThat(service.getRun(run.runId()).tokenUsage().output()).isEqualTo(20L);
        assertThat(service.getRun(run.runId()).costUsd()).isEqualByComparingTo("2.00000000");
    }

    @Test
    void serviceMarksRunFailedWhenOpencodeStreamEmitsTerminalEvent() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.RUN_FAILED,
                command.traceId(),
                Instant.now(),
                Map.of("messageID", "msg_remote1234567890abcdef")));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        awaitRunStatus(service, run.runId(), RunStatus.FAILED);
        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.RUN_FAILED);
    }

    @Test
    void serviceDoesNotFailRunWhenPersistingNonTerminalStreamEventFails() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        events.failOnType = RunEventType.TOOL_FINISHED;
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.TOOL_FINISHED,
                command.traceId(),
                Instant.now(),
                Map.of("tool", "bash", "status", "completed")));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        Run run = service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitFailedAppendAttempts(events, 1);
        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(service.getRun(run.runId()).status()).isEqualTo(RunStatus.RUNNING);
        assertThat(events.events).extracting(RunEvent::type)
                .containsExactly(RunEventType.RUN_CREATED, RunEventType.RUN_STARTED);
    }

    @Test
    void servicePublishesMessageProjectionToLiveBusWithoutPersisting() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(
                new RunEventDraft(
                        command.runId(),
                        RunEventType.MESSAGE_PART_DELTA,
                        command.traceId(),
                        Instant.now(),
                        Map.of(
                                "messageID", "msg_1",
                                "partID", "part_1",
                                "delta", "hello",
                                "rawPayload", Map.of("full", "event"))),
                new RunEventDraft(
                        command.runId(),
                        RunEventType.MESSAGE_UPDATED,
                        command.traceId(),
                        Instant.now(),
                        Map.of("message", Map.of("id", "msg_1", "role", "assistant"))));
        RecordingRunEventLiveBus liveBus = new RecordingRunEventLiveBus();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                liveBus,
                new RunEventPersistencePolicy());

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitLiveEvents(liveBus, 2);
        assertThat(events.events).extracting(RunEvent::type)
                .containsExactly(RunEventType.RUN_CREATED, RunEventType.RUN_STARTED);
        assertThat(liveBus.transientPayloads).extracting(RunEventSsePayload::type)
                .containsExactly("message.part.delta", "message.updated");
        assertThat(liveBus.transientPayloads).allSatisfy(payload -> assertThat(payload.seq()).isZero());
        assertThat(liveBus.transientPayloads.get(0).payload()).doesNotContainKey("rawPayload");
    }

    @Test
    void serviceRecordsOutputActivityAndAskStateForVisibleRuntimeEvents() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(
                new RunEventDraft(
                        command.runId(),
                        RunEventType.MESSAGE_PART_DELTA,
                        command.traceId(),
                        Instant.now(),
                        Map.of("messageID", "msg_1", "partID", "part_1", "delta", "hello")),
                new RunEventDraft(
                        command.runId(),
                        RunEventType.QUESTION_ASKED,
                        command.traceId(),
                        Instant.now(),
                        Map.of("requestId", "question_1")),
                new RunEventDraft(
                        command.runId(),
                        RunEventType.QUESTION_REPLIED,
                        command.traceId(),
                        Instant.now(),
                        Map.of("requestId", "question_1")),
                new RunEventDraft(
                        command.runId(),
                        RunEventType.OPENCODE_EVENT_UNKNOWN,
                        command.traceId(),
                        Instant.now(),
                        Map.of("rawType", "server.heartbeat")));
        RecordingRunActivityStateStore activityStore = new RecordingRunActivityStateStore();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                null,
                ManagedWorkspacePathResolver.legacyOnly(),
                null,
                null,
                null,
                activityStore);

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitActivityEvents(activityStore, 3);
        assertThat(activityStore.outputRunIds).hasSize(3);
        assertThat(activityStore.pendingAskRunIds).hasSize(1);
        assertThat(activityStore.clearedAskRunIds).hasSize(1);
    }

    @Test
    void serviceReconcilesActiveRunWhenInteractionReplyProducesFinalAssistantMessage() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                        command.runId(),
                        RunEventType.QUESTION_ASKED,
                        command.traceId(),
                        Instant.now(),
                        Map.of("requestId", "question_1")))
                .concatWith(Flux.never());
        facade.sessionMessagesResult = new OpencodeSessionMessagesResult(
                List.of(new OpencodeSessionMessage(
                        Map.of(
                                "id", "msg_final_1234567890abcdef",
                                "role", "assistant",
                                "finish", "stop"),
                        List.of(Map.of(
                                "id", "part_final_1234567890abcdef",
                                "type", "text",
                                "text", "A")))),
                null,
                null);
        RecordingRunEventLiveBus liveBus = new RecordingRunEventLiveBus();
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                liveBus,
                new RunEventPersistencePolicy());
        Run run = service.startRun(
                new SessionId("ses_1234567890abcdef"),
                "ask before finishing",
                "trace_1234567890abcdef");

        service.reconcileAfterInteractionReply(
                new SessionId("ses_1234567890abcdef"),
                "opencode",
                "trace_1234567890abcdef");

        awaitRunStatus(service, run.runId(), RunStatus.SUCCEEDED);
        awaitEventTypes(
                events,
                RunEventType.RUN_CREATED,
                RunEventType.RUN_STARTED,
                RunEventType.QUESTION_ASKED,
                RunEventType.RUN_SUCCEEDED);
        assertThat(events.events).extracting(RunEvent::type)
                .contains(RunEventType.RUN_SUCCEEDED);
        assertThat(liveBus.transientPayloads).extracting(RunEventSsePayload::type)
                .containsExactly("message.updated", "message.part.updated");
        assertThat(liveBus.transientPayloads.get(1).payload())
                .containsEntry("messageID", "msg_final_1234567890abcdef");
    }

    @Test
    void serviceRecordsQuestionReplyWhenRemoteEventStreamMissesTheNativeEvent() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                        command.runId(),
                        RunEventType.QUESTION_ASKED,
                        command.traceId(),
                        Instant.now(),
                        Map.of("requestID", "question_1")))
                .concatWith(Flux.never());
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());
        service.startRun(
                new SessionId("ses_1234567890abcdef"),
                "ask before finishing",
                "trace_1234567890abcdef");
        awaitEventTypes(
                events,
                RunEventType.RUN_CREATED,
                RunEventType.RUN_STARTED,
                RunEventType.QUESTION_ASKED);

        service.recordQuestionReplyAcknowledged(
                new SessionId("ses_1234567890abcdef"),
                "ses_remote1234567890abcdef",
                "question_1",
                List.of(List.of("B")),
                "trace_1234567890abcdef");

        assertThat(events.events).extracting(RunEvent::type)
                .containsSubsequence(RunEventType.QUESTION_ASKED, RunEventType.QUESTION_REPLIED);
        RunEvent replied = events.events.stream()
                .filter(event -> event.type() == RunEventType.QUESTION_REPLIED)
                .findFirst()
                .orElseThrow();
        assertThat(replied.payload()).containsEntry("requestID", "question_1");
        assertThat(replied.payload()).containsEntry("answers", List.of(List.of("B")));
        assertThat(replied.payload()).containsEntry("source", "interaction_reply_ack");
    }

    @Test
    void activeRunLookupConvergesWhenRemoteFinalMessageWasMissedByEventStream() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeAgentSessionBindingRepository bindings = new FakeAgentSessionBindingRepository();
        facade.streamEvents = ignored -> Flux.never();
        facade.sessionMessagesResult = new OpencodeSessionMessagesResult(
                List.of(new OpencodeSessionMessage(
                        Map.of(
                                "id", "msg_final_lookup_1234567890",
                                "role", "assistant",
                                "finish", "stop",
                                "time", Map.of("created", NOW.plusSeconds(1).toEpochMilli(), "completed", NOW.plusSeconds(2).toEpochMilli())),
                        List.of(Map.of(
                                "id", "part_final_lookup_1234567890",
                                "type", "text",
                                "text", "已完成")))),
                null,
                null);
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                bindings);
        Run run = new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.RUNNING,
                NOW,
                NOW,
                "trace_1234567890abcdef").withRuntimeSelection("build", null);
        runs.save(run);
        bindings.save(new AgentSessionBinding(
                run.sessionId(),
                "opencode",
                REMOTE_SESSION_ID,
                node().executionNodeId(),
                NOW,
                NOW,
                "trace_1234567890abcdef"));

        Optional<Run> activeRun = service.findActiveRun(run.sessionId());
        assertThat(facade.sessionMessagesCommands).extracting(OpencodeSessionMessagesCommand::order)
                .contains("desc", "asc");
        assertThat(runs.saved).extracting(Run::status).contains(RunStatus.SUCCEEDED);
        assertThat(activeRun).isEmpty();
        assertThat(service.getRun(run.runId()).status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(events.events).extracting(RunEvent::type).containsExactly(RunEventType.RUN_SUCCEEDED);
    }

    @Test
    void activeRunLookupDoesNotConvergeFromAssistantMessageBeforeRun() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        FakeAgentSessionBindingRepository bindings = new FakeAgentSessionBindingRepository();
        facade.sessionMessagesResult = new OpencodeSessionMessagesResult(
                List.of(new OpencodeSessionMessage(
                        Map.of(
                                "id", "msg_previous_1234567890",
                                "role", "assistant",
                                "finish", "stop",
                                "time", Map.of("created", NOW.minusSeconds(2).toEpochMilli())),
                        List.of())),
                null,
                null);
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                bindings);
        Run run = new Run(
                new RunId("run_previous_guard_123456"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.RUNNING,
                NOW,
                NOW,
                "trace_previous_guard_123").withRuntimeSelection("build", null);
        runs.save(run);
        bindings.save(new AgentSessionBinding(
                run.sessionId(),
                "opencode",
                REMOTE_SESSION_ID,
                node().executionNodeId(),
                NOW,
                NOW,
                "trace_previous_guard_123"));

        assertThat(service.findActiveRun(run.sessionId())).contains(run);
        assertThat(service.getRun(run.runId()).status()).isEqualTo(RunStatus.RUNNING);
        assertThat(events.events).isEmpty();
    }

    @Test
    void servicePersistsLiveDiffFromCompletedEditToolPart() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.MESSAGE_PART_UPDATED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "messageID", "msg_1",
                        "part", Map.of(
                                "id", "part_edit",
                                "messageID", "msg_1",
                                "type", "tool",
                                "tool", "edit",
                                "state", Map.of(
                                        "status", "completed",
                                        "input", Map.of("filePath", "/tmp/demo/src/App.ts"),
                                        "metadata", Map.of("filediff", Map.of(
                                                "file", "src/App.ts",
                                                "patch", "@@ -1 +1,2 @@",
                                                "additions", 2,
                                                "deletions", 1)))))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        service.startRun(new SessionId("ses_1234567890abcdef"), "edit app", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.DIFF_PROPOSED);
        Map<String, Object> payload = events.events.get(2).payload();
        assertThat(payload)
                .containsEntry("source", "tool")
                .containsEntry("tool", "edit")
                .containsEntry("messageID", "msg_1")
                .containsEntry("partID", "part_edit");
        assertThat(payload).doesNotContainKeys("rawPayload", "input", "output", "metadata");
        assertThat((List<?>) payload.get("files"))
                .singleElement()
                .satisfies(file -> assertThat(mapObject(file))
                        .containsEntry("path", "src/App.ts")
                        .containsEntry("patch", "@@ -1 +1,2 @@")
                        .containsEntry("additions", 2)
                        .containsEntry("deletions", 1)
                        .containsEntry("status", "modified"));
    }

    @Test
    void servicePersistsLiveDiffFromCompletedApplyPatchToolPart() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.MESSAGE_PART_UPDATED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "messageID", "msg_1",
                        "part", Map.of(
                                "id", "part_patch",
                                "messageID", "msg_1",
                                "type", "tool",
                                "tool", "apply_patch",
                                "state", Map.of(
                                        "status", "completed",
                                        "metadata", Map.of("files", List.of(
                                                Map.of(
                                                        "relativePath", "src/New.ts",
                                                        "type", "add",
                                                        "patch", "@@ new @@",
                                                        "additions", 3,
                                                        "deletions", 0),
                                                Map.of(
                                                        "relativePath", "src/Old.ts",
                                                        "type", "delete",
                                                        "patch", "@@ old @@",
                                                        "additions", 0,
                                                        "deletions", 4))))))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        service.startRun(new SessionId("ses_1234567890abcdef"), "patch files", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.DIFF_PROPOSED);
        Map<String, Object> payload = events.events.get(2).payload();
        assertThat((List<?>) payload.get("files"))
                .hasSize(2)
                .satisfies(files -> {
                    assertThat(mapObject(files.get(0)))
                            .containsEntry("path", "src/New.ts")
                            .containsEntry("status", "added")
                            .containsEntry("additions", 3)
                            .containsEntry("deletions", 0);
                    assertThat(mapObject(files.get(1)))
                            .containsEntry("path", "src/Old.ts")
                            .containsEntry("status", "deleted")
                            .containsEntry("additions", 0)
                            .containsEntry("deletions", 4);
                });
    }

    @Test
    void servicePublishesWritePathWithoutCallingUnsupportedWorkingTreeDiff() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.MESSAGE_PART_UPDATED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "messageID", "msg_1",
                        "part", Map.of(
                                "id", "part_write",
                                "messageID", "msg_1",
                                "type", "tool",
                                "tool", "write",
                                "state", Map.of(
                                        "status", "completed",
                                        "input", Map.of("filePath", "/tmp/demo/src/Write.ts"),
                                        "metadata", Map.of("filepath", "/tmp/demo/src/Write.ts"))))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        service.startRun(new SessionId("ses_1234567890abcdef"), "write file", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.DIFF_PROPOSED);
        assertThat(facade.runtimeCommands).isEmpty();
        assertThat((List<?>) events.events.get(2).payload().get("files"))
                .singleElement()
                .satisfies(file -> assertThat(mapObject(file))
                        .containsEntry("path", "src/Write.ts")
                        .containsEntry("additions", 0)
                        .containsEntry("deletions", 0));
    }

    @Test
    void servicePersistsSanitizedToolFinishedPayload() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.streamEvents = command -> Flux.just(new RunEventDraft(
                command.runId(),
                RunEventType.TOOL_FINISHED,
                command.traceId(),
                Instant.now(),
                Map.of(
                        "tool", "bash",
                        "callID", "call_1",
                        "messageID", "msg_1",
                        "partID", "part_1",
                        "status", "completed",
                        "title", "Bash",
                        "rawPayload", Map.of("full", "event"),
                        "output", "very long bash output",
                        "input", Map.of("command", "cat large.log"),
                        "metadata", Map.of("large", "metadata"))));
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new RecordingRunEventLiveBus(),
                new RunEventPersistencePolicy());

        service.startRun(new SessionId("ses_1234567890abcdef"), "run the tests", "trace_1234567890abcdef");

        awaitEventTypes(events, RunEventType.RUN_CREATED, RunEventType.RUN_STARTED, RunEventType.TOOL_FINISHED);
        Map<String, Object> payload = events.events.get(2).payload();
        assertThat(payload).containsEntry("tool", "bash");
        assertThat(payload).containsEntry("callID", "call_1");
        assertThat(payload).containsEntry("status", "completed");
        assertThat(payload).doesNotContainKeys("rawPayload", "output", "input", "metadata");
    }

    @Test
    void serviceFailsRunWhenMappedExecutionNodeCannotAcceptRun() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        ExecutionNode offlineNode = node(ExecutionNodeStatus.OFFLINE, 0, 4);
        Session mappedSession = session().attachOpencodeSession(
                REMOTE_SESSION_ID,
                offlineNode.executionNodeId(),
                NOW.plusSeconds(1),
                "trace_1234567890abcdef");
        FakeExecutionNodeRepository nodes = new FakeExecutionNodeRepository(offlineNode);
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(mappedSession),
                runs,
                new FakeSessionMessageRepository(),
                nodes,
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        assertThatThrownBy(() -> service.startRun(
                        new SessionId("ses_1234567890abcdef"),
                        "run while node offline",
                        "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThat(facade.createSessionCommands).isEmpty();
        assertThat(facade.startRunCommands).isEmpty();
        assertThat(runs.saved).extracting(Run::status).contains(RunStatus.PENDING, RunStatus.FAILED);
        assertThat(events.events).extracting(RunEvent::type)
                .containsExactly(RunEventType.RUN_CREATED, RunEventType.RUN_FAILED);
    }

    @Test
    void serviceMarksRunFailedWhenOpencodeSessionCreationFails() {
        FakeRunRepository runs = new FakeRunRepository();
        FakeRunEventRepository events = new FakeRunEventRepository();
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.createSessionError = new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "opencode unavailable",
                Map.of("nodeId", node().executionNodeId().value()));
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        RunApplicationService service = new RunApplicationService(
                new FakeWorkspaceRepository(),
                sessions,
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        assertThatThrownBy(() -> service.startRun(
                        new SessionId("ses_1234567890abcdef"),
                        "run the tests",
                        "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThat(sessions.current.opencodeSessionId()).isNull();
        assertThat(runs.saved).extracting(Run::status).contains(RunStatus.PENDING, RunStatus.FAILED);
        assertThat(facade.startRunCommands).isEmpty();
        assertThat(events.events).extracting(RunEvent::type)
                .containsExactly(RunEventType.RUN_CREATED, RunEventType.RUN_FAILED);
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
                runtimeRegistry(new FakeOpencodeFacade()),
                new FakeAgentSessionBindingRepository());

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
        return node(ExecutionNodeStatus.READY, 0, 4);
    }

    private static ExecutionNode node(ExecutionNodeStatus status, int runningRuns, int maxRuns) {
        return new ExecutionNode(
                new ExecutionNodeId("node_1234567890abcdef"),
                "http://127.0.0.1:4096",
                status,
                runningRuns,
                maxRuns,
                100,
                NOW,
                Set.of("chat"),
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static ExecutionNode userProcessNode(String nodeId, String baseUrl) {
        return new ExecutionNode(
                new ExecutionNodeId(nodeId),
                baseUrl,
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                NOW,
                Set.of("opencode", "user-process"),
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static void awaitRunStatus(RunApplicationService service, RunId runId, RunStatus expected) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (service.getRun(runId).status() == expected) {
                return;
            }
            sleepBriefly();
        }
        assertThat(service.getRun(runId).status()).isEqualTo(expected);
    }

    private static void awaitEventTypes(FakeRunEventRepository events, RunEventType... expected) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (events.events.size() == expected.length) {
                break;
            }
            sleepBriefly();
        }
        assertThat(events.events).extracting(RunEvent::type).containsExactly(expected);
    }

    private static void awaitFailedAppendAttempts(FakeRunEventRepository events, int expected) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (events.failedAppendAttempts >= expected) {
                return;
            }
            sleepBriefly();
        }
        assertThat(events.failedAppendAttempts).isGreaterThanOrEqualTo(expected);
    }

    private static void awaitLiveEvents(RecordingRunEventLiveBus liveBus, int expected) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (liveBus.transientPayloads.size() >= expected) {
                return;
            }
            sleepBriefly();
        }
        assertThat(liveBus.transientPayloads).hasSizeGreaterThanOrEqualTo(expected);
    }

    private static void awaitActivityEvents(RecordingRunActivityStateStore activityStore, int expectedOutputEvents) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (activityStore.outputRunIds.size() >= expectedOutputEvents) {
                return;
            }
            sleepBriefly();
        }
        assertThat(activityStore.outputRunIds).hasSizeGreaterThanOrEqualTo(expectedOutputEvents);
    }

    private static void awaitMessageCount(FakeSessionMessageRepository messages, int expected) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (messages.saved.size() >= expected) {
                return;
            }
            sleepBriefly();
        }
        assertThat(messages.saved).hasSizeGreaterThanOrEqualTo(expected);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for async stream event", exception);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for async stream event", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapObject(Object value) {
        return (Map<String, Object>) value;
    }

    private static RunRuntimeStore mockRuntimeStoreForStart(AtomicReference<RunRuntimeManifest> state) {
        RunRuntimeStore store = org.mockito.Mockito.mock(RunRuntimeStore.class);
        org.mockito.Mockito.when(store.findByClientRequest(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.when(store.claimClientRequest(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        org.mockito.Mockito.when(store.confirmClientRequest(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            state.set(invocation.getArgument(0));
            return null;
        }).when(store).initialize(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.when(store.findManifest(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> Optional.ofNullable(state.get()));
        org.mockito.stubbing.Answer<RunRuntimeAppendResult> appendAnswer = invocation -> {
                    RunEventDraft draft = invocation.getArgument(0);
                    RunRuntimeManifest current = state.get();
                    RunStatus nextStatus = switch (draft.type()) {
                        case RUN_STARTED -> RunStatus.RUNNING;
                        case RUN_SUCCEEDED -> RunStatus.SUCCEEDED;
                        case RUN_FAILED -> RunStatus.FAILED;
                        case RUN_CANCELLED -> RunStatus.CANCELLED;
                        default -> current.status();
                    };
                    long statusVersion = current.statusVersion()
                            + (nextStatus == current.status() ? 0L : 1L);
                    long seq = current.lastSeq() + 1L;
                    RunRuntimeManifest next = new RunRuntimeManifest(
                            current.runId(), current.storageMode(), current.userId(), current.sessionId(),
                            current.workspaceId(), current.agentId(), current.clientRequestId(),
                            current.dispatchMessageId(), current.producerLinuxServerId(), current.backendProcessId(),
                            current.executionNodeId(), current.opencodeProcessId(), current.rootRemoteSessionId(),
                            nextStatus, statusVersion, seq, current.earliestSeq(), current.resetGeneration(),
                            current.detailsTruncated(), current.durableEventCount() + 1L, current.detailBytes(),
                            current.attention(), current.attentionEventId(), current.attentionAt(),
                            current.detailsExpiresAt(), current.createdAt(), Instant.now());
                    state.set(next);
                    RunEvent event = new RunEvent(
                            new RunEventId("evt_runtime_store_" + seq),
                            draft.runId(),
                            seq,
                            draft.type(),
                            draft.traceId(),
                            draft.occurredAt(),
                            draft.payload(),
                            draft.scopeContext());
                    return new RunRuntimeAppendResult(event, false, 0L, 1L);
                };
        org.mockito.Mockito.when(store.appendDurable(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(appendAnswer);
        org.mockito.Mockito.when(store.appendDurable(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(RunOwnerLease.class)))
                .thenAnswer(appendAnswer);
        org.mockito.Mockito.when(store.projectTransient(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.when(store.projectTransient(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(RunOwnerLease.class)))
                .thenReturn(true);
        return store;
    }

    private static final class FakeWorkspaceRepository implements WorkspaceRepository {
        private int findByIdCalls;

        @Override
        public Workspace save(Workspace workspace) {
            return workspace;
        }

        @Override
        public Optional<Workspace> findById(WorkspaceId workspaceId) {
            findByIdCalls++;
            return Optional.of(workspace());
        }

        @Override
        public PageResponse<Workspace> findPage(PageRequest pageRequest) {
            return new PageResponse<>(List.of(workspace()), pageRequest.page(), pageRequest.size(), 1);
        }
    }

    private static final class FakeSessionRepository implements com.icbc.testagent.domain.session.SessionRepository {
        private Session current;
        private boolean failTitleUpdate;
        private String remoteSessionIdOverride;
        private int findByIdCalls;

        private FakeSessionRepository() {
            this(session());
        }

        private FakeSessionRepository(Session current) {
            this.current = current;
        }

        @Override
        public Session save(Session session) {
            if (failTitleUpdate && "AI 标题保存失败".equals(session.title())) {
                throw new DataAccessResourceFailureException("title save failed");
            }
            current = session;
            return session;
        }

        @Override
        public Optional<Session> findById(SessionId sessionId) {
            findByIdCalls++;
            return Optional.of(current);
        }

        @Override
        public PageResponse<Session> findPage(String query, PageRequest pageRequest) {
            return new PageResponse<>(List.of(current), pageRequest.page(), pageRequest.size(), 1);
        }

        @Override
        public PageResponse<Session> findByWorkspaceId(WorkspaceId workspaceId, PageRequest pageRequest) {
            return new PageResponse<>(List.of(current), pageRequest.page(), pageRequest.size(), 1);
        }

        @Override
        public Optional<Session> attachOpencodeSession(
                SessionId sessionId,
                String opencodeSessionId,
                ExecutionNodeId executionNodeId,
                Instant updatedAt,
                String traceId) {
            current = current.attachOpencodeSession(
                    remoteSessionIdOverride == null ? opencodeSessionId : remoteSessionIdOverride,
                    executionNodeId,
                    updatedAt,
                    traceId);
            return Optional.of(current);
        }
    }

    private static class FakeRunRepository implements RunRepository {
        private final List<Run> saved = new CopyOnWriteArrayList<>();
        private final Map<RunId, Run> currentByRunId = new LinkedHashMap<>();

        @Override
        public Run save(Run run) {
            currentByRunId.put(run.runId(), run);
            saved.add(run);
            return run;
        }

        @Override
        public Optional<Run> findById(RunId runId) {
            return Optional.ofNullable(currentByRunId.get(runId));
        }

        @Override
        public Optional<Run> findLatestActiveBySessionId(SessionId sessionId) {
            return currentByRunId.values().stream()
                    .filter(run -> run.sessionId().equals(sessionId))
                    .filter(run -> !run.status().isTerminal())
                    .reduce((first, second) -> second);
        }
    }

    private static final class TerminalRaceRunRepository extends FakeRunRepository {
        private final CountDownLatch failedSaveStarted = new CountDownLatch(1);
        private final CountDownLatch succeededSaved = new CountDownLatch(1);

        @Override
        public Run save(Run run) {
            if (run.status() == RunStatus.FAILED) {
                failedSaveStarted.countDown();
                await(succeededSaved);
            }
            Run saved = super.save(run);
            if (run.status() == RunStatus.SUCCEEDED) {
                succeededSaved.countDown();
            }
            return saved;
        }

        @Override
        public Run saveIfStatus(Run run, RunStatus expectedStatus) {
            if (run.status() == RunStatus.FAILED) {
                failedSaveStarted.countDown();
                await(succeededSaved);
            }
            Optional<Run> current = findById(run.runId());
            if (current.isPresent() && current.get().status() != expectedStatus) {
                return current.get();
            }
            Run saved = super.save(run);
            if (run.status() == RunStatus.SUCCEEDED) {
                succeededSaved.countDown();
            }
            return saved;
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting for concurrent terminal transition");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for concurrent terminal transition", exception);
            }
        }
    }

    private static final class CountingSnapshotService extends RunSessionMessageSnapshotService {
        private int persistCalls;

        private CountingSnapshotService(
                RunRepository runs,
                FakeSessionRepository sessions,
                SessionMessageRepository messages,
                ExecutionNodeRepository nodes,
                AgentRuntimeRegistry registry,
                AgentSessionBindingRepository bindings) {
            super(runs, sessions, messages, nodes, registry, bindings, new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Override
        public boolean persistRunSnapshot(String agentId, Run run, String traceId) {
            persistCalls++;
            return true;
        }
    }

    private static final class FakeSessionMessageRepository implements SessionMessageRepository {
        private final List<SessionMessage> saved = new CopyOnWriteArrayList<>();

        @Override
        public SessionMessage save(SessionMessage message) {
            saved.add(message);
            return message;
        }

        @Override
        public Optional<SessionMessage> findById(SessionMessageId messageId) {
            return saved.stream()
                    .filter(message -> message.messageId().equals(messageId))
                    .reduce((first, second) -> second);
        }

        @Override
        public Optional<SessionMessage> findBySessionIdAndRemoteMessageId(SessionId sessionId, String remoteMessageId) {
            return saved.stream()
                    .filter(message -> message.sessionId().equals(sessionId))
                    .filter(message -> remoteMessageId != null && remoteMessageId.equals(message.remoteMessageId()))
                    .reduce((first, second) -> second);
        }

        @Override
        public PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest) {
            return new PageResponse<>(saved, pageRequest.page(), pageRequest.size(), saved.size());
        }
    }

    private static final class FakeExecutionNodeRepository implements ExecutionNodeRepository {
        private final ExecutionNode node;
        private final List<ExecutionNode> saved = new ArrayList<>();
        private int findRoutableNodesCalls;
        private int findByIdCalls;

        private FakeExecutionNodeRepository() {
            this(node());
        }

        private FakeExecutionNodeRepository(ExecutionNode node) {
            this.node = node;
        }

        @Override
        public ExecutionNode save(ExecutionNode executionNode) {
            saved.add(executionNode);
            return executionNode;
        }

        @Override
        public Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId) {
            findByIdCalls++;
            return Optional.of(node);
        }

        @Override
        public List<ExecutionNode> findRoutableNodes(int limit) {
            findRoutableNodesCalls++;
            return List.of(node);
        }
    }

    private static final class FakeRoutingDecisionRepository implements RoutingDecisionRepository {
        private final List<RoutingDecision> saved = new ArrayList<>();

        @Override
        public RoutingDecision save(RoutingDecision routingDecision) {
            saved.add(routingDecision);
            return routingDecision;
        }

        @Override
        public Optional<RoutingDecision> findByRunId(RunId runId) {
            return Optional.empty();
        }
    }

    private static final class FakeRunSessionScopeRepository implements RunSessionScopeRepository {
        private final Map<String, RunSessionScopeSession> sessions = new LinkedHashMap<>();

        @Override
        public void upsertScope(RunSessionScope scope) {
        }

        @Override
        public void upsertSession(RunSessionScopeSession session) {
            sessions.put(session.sessionId(), session);
        }

        @Override
        public List<RunSessionScopeSession> findSessionsByRunId(RunId runId) {
            return sessions.values().stream()
                    .filter(session -> session.runId().equals(runId))
                    .toList();
        }

        @Override
        public List<RunSessionScopeSession> findSessionsByRootSessionId(String rootSessionId) {
            return sessions.values().stream()
                    .filter(session -> session.rootSessionId().equals(rootSessionId))
                    .toList();
        }

        @Override
        public Optional<RunSessionScopeSession> findSession(RunId runId, String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId))
                    .filter(session -> session.runId().equals(runId));
        }
    }

    private static final class FakeRunEventRepository implements RunEventRepository {
        private final List<RunEvent> events = new ArrayList<>();
        private RunEventType failOnType;
        private int failedAppendAttempts;

        @Override
        public RunEvent append(RunEventDraft draft) {
            if (draft.type() == failOnType) {
                failedAppendAttempts++;
                throw new DataAccessResourceFailureException("connection closed");
            }
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

    private static final class RecordingRunEventLiveBus extends RunEventLiveBus {
        private final List<RunEventSsePayload> transientPayloads = new CopyOnWriteArrayList<>();

        @Override
        public RunEventLiveEvent publishTransient(RunEventDraft draft) {
            RunEventLiveEvent event = super.publishTransient(draft);
            transientPayloads.add(event.payload());
            return event;
        }
    }

    private static final class RecordingRunActivityStateStore extends RunActivityStateStore {
        private final List<RunId> outputRunIds = new CopyOnWriteArrayList<>();
        private final List<RunId> pendingAskRunIds = new CopyOnWriteArrayList<>();
        private final List<RunId> clearedAskRunIds = new CopyOnWriteArrayList<>();

        private RecordingRunActivityStateStore() {
            super(null);
        }

        @Override
        void recordOutput(RunId runId, Instant occurredAt) {
            outputRunIds.add(runId);
        }

        @Override
        void markPendingAsk(RunId runId, RunEventType type, Instant occurredAt) {
            pendingAskRunIds.add(runId);
        }

        @Override
        void clearPendingAsk(RunId runId) {
            clearedAskRunIds.add(runId);
        }
    }

    private static AgentRuntimeRegistry runtimeRegistry(FakeOpencodeFacade facade) {
        return new AgentRuntimeRegistry(List.of(new OpencodeAgentRuntime(facade)));
    }

    private static final class FakeAgentSessionBindingRepository implements AgentSessionBindingRepository {
        private final Map<String, AgentSessionBinding> bindings = new LinkedHashMap<>();
        private int findBySessionIdAndAgentIdCalls;

        @Override
        public AgentSessionBinding save(AgentSessionBinding binding) {
            bindings.put(key(binding.sessionId(), binding.agentId()), binding);
            return binding;
        }

        @Override
        public Optional<AgentSessionBinding> findBySessionIdAndAgentId(SessionId sessionId, String agentId) {
            findBySessionIdAndAgentIdCalls++;
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

    private static final class FakeOpencodeFacade implements OpencodeClientFacade {
        private final List<OpencodeCreateSessionCommand> createSessionCommands = new ArrayList<>();
        private final List<OpencodeStartRunCommand> startRunCommands = new ArrayList<>();
        private final List<OpencodeCancelCommand> cancelSessionCommands = new ArrayList<>();
        private final List<OpencodeStartCommand> startCommandCommands = new ArrayList<>();
        private final List<OpencodeRuntimeCommand> runtimeCommands = new ArrayList<>();
        private final List<OpencodeSessionMessagesCommand> sessionMessagesCommands = new ArrayList<>();
        private final List<String> callOrder = new ArrayList<>();
        private String lastPrompt;
        private RuntimeException createSessionError;
        private OpencodeSessionMessagesResult sessionMessagesResult = new OpencodeSessionMessagesResult(List.of(), null, null);
        private Function<OpencodeSessionMessagesCommand, Mono<OpencodeSessionMessagesResult>> sessionMessages =
                command -> Mono.just(sessionMessagesResult);
        private Function<OpencodeStreamEventsCommand, Flux<RunEventDraft>> streamEvents = ignored -> Flux.empty();
        private Function<OpencodeStartRunCommand, Mono<OpencodeStartRunResult>> startRun =
                ignored -> Mono.just(new OpencodeStartRunResult(true));
        private Function<OpencodeRuntimeCommand, Mono<OpencodeRuntimeResult>> runtime =
                ignored -> Mono.just(new OpencodeRuntimeResult(JsonNodeFactory.instance.objectNode()));

        @Override
        public Mono<OpencodeHealthResult> health(OpencodeHealthCommand command) {
            return Mono.just(new OpencodeHealthResult(true, command.node().baseUrl()));
        }

        @Override
        public Mono<OpencodeCreateSessionResult> createSession(OpencodeCreateSessionCommand command) {
            createSessionCommands.add(command);
            if (createSessionError != null) {
                return Mono.error(createSessionError);
            }
            return Mono.just(new OpencodeCreateSessionResult(REMOTE_SESSION_ID));
        }

        @Override
        public Mono<Boolean> sessionExists(OpencodeSessionExistsCommand command) {
            return Mono.just(true);
        }

        @Override
        public Mono<OpencodeCancelResult> cancelSession(OpencodeCancelCommand command) {
            cancelSessionCommands.add(command);
            return Mono.just(new OpencodeCancelResult(true));
        }

        @Override
        public Mono<OpencodeStartRunResult> startRun(OpencodeStartRunCommand command) {
            callOrder.add("startRun");
            startRunCommands.add(command);
            lastPrompt = command.prompt();
            return startRun.apply(command);
        }

        @Override
        public Mono<OpencodeStartRunResult> startCommand(OpencodeStartCommand command) {
            callOrder.add("startCommand");
            startCommandCommands.add(command);
            return Mono.just(new OpencodeStartRunResult(true));
        }

        @Override
        public Flux<RunEventDraft> streamRunEvents(OpencodeStreamEventsCommand command) {
            callOrder.add("streamRunEvents");
            return streamEvents.apply(command);
        }

        @Override
        public Mono<OpencodeDiffResult> getDiff(OpencodeDiffCommand command) {
            return Mono.just(new OpencodeDiffResult(List.of()));
        }

        @Override
        public Mono<OpencodeRejectDiffResult> rejectDiff(OpencodeRejectDiffCommand command) {
            return Mono.just(new OpencodeRejectDiffResult(true));
        }

        @Override
        public Mono<OpencodeRuntimeResult> runtime(OpencodeRuntimeCommand command) {
            return runtime.apply(command);
        }

        @Override
        public Mono<OpencodeSessionMessagesResult> sessionMessages(OpencodeSessionMessagesCommand command) {
            sessionMessagesCommands.add(command);
            return sessionMessages.apply(command);
        }
    }

    private static RunApplicationService serviceWithModelCatalog(
            FakeOpencodeFacade facade,
            FakeRunRepository runs,
            FakeRunEventRepository events,
            ModelCatalogApplicationService modelCatalog) {
        return new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                runs,
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(events),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                modelCatalog,
                null,
                com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver.legacyOnly(),
                null,
                null,
                null);
    }

    private static RunApplicationService serviceWithModelCatalogAndAssignment(
            FakeOpencodeFacade facade,
            ModelCatalogApplicationService modelCatalog,
            UserOpencodeProcessAssignmentService assignmentService) {
        return new RunApplicationService(
                new FakeWorkspaceRepository(),
                new FakeSessionRepository(session()),
                new FakeRunRepository(),
                new FakeSessionMessageRepository(),
                new FakeExecutionNodeRepository(),
                new FakeRoutingDecisionRepository(),
                new RunEventAppender(new FakeRunEventRepository()),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                modelCatalog,
                assignmentService,
                com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver.legacyOnly(),
                null,
                null,
                null);
    }

    private static ModelCatalogApplicationService managedModelCatalog(List<Map<String, Object>> models) {
        ModelCatalogApplicationService service = org.mockito.Mockito.mock(ModelCatalogApplicationService.class);
        org.mockito.Mockito.when(service.managedSourceEnabled()).thenReturn(true);
        org.mockito.Mockito.when(service.listModels()).thenReturn(models);
        return service;
    }

    private static void startRunWithModel(RunApplicationService service, String model) {
        service.startRun(new StartRunInput(
                        new SessionId("ses_1234567890abcdef"),
                        "run with selected model",
                        List.of(),
                        null,
                        "build",
                        model,
                        null,
                        null),
                "trace_1234567890abcdef");
    }

    private static Map<String, Object> modelPayload(String providerId, String modelId, boolean defaultModel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", modelId);
        payload.put("modelId", modelId);
        payload.put("modelID", modelId);
        payload.put("providerId", providerId);
        payload.put("providerID", providerId);
        payload.put("name", modelId);
        payload.put("defaultModel", defaultModel);
        return payload;
    }
}
