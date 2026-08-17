package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentCreateSessionResult;
import com.enterprise.testagent.agent.runtime.AgentEventStream;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentStartRunCommand;
import com.enterprise.testagent.agent.runtime.AgentStartRunResult;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.hub.ProtectedAgentDefinitionResolver;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.routing.RoutingDecisionRepository;
import com.enterprise.testagent.domain.run.ConversationRunContext;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.event.RunEventLiveBus;
import com.enterprise.testagent.opencode.runtime.protectedagent.ProtectedAgentExecutionService;
import com.enterprise.testagent.opencode.runtime.protectedagent.ProtectedOpencodeAgentRuntime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ProtectedAgentRunApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_protectedrun1234567890");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_protectedrun1234567890");
    private static final SessionId SESSION_ID = new SessionId("ses_protectedrun1234567890");

    @Test
    void protectedSelectionValidatesLocalContextButDispatchesOnlyToServerRuntime() {
        Workspace workspace = new Workspace(WORKSPACE_ID, "local", "/Users/test/workspace", NOW);
        Session session = new Session(SESSION_ID, WORKSPACE_ID, "protected", NOW)
                .withSource(com.enterprise.testagent.domain.session.ConversationSourceType.MANUAL, null, USER_ID);
        ExecutionNode localNode = localNode();
        ExecutionNode serverNode = serverNode();
        StartRunInput input = new StartRunInput(
                SESSION_ID,
                "检查本地 README 并给出结论",
                List.of(),
                null,
                "protected:hub_rev_agent_1",
                null,
                null,
                "build",
                null,
                null,
                "ctx_protected_run",
                "request_protected_run");
        ConversationRunContext localContext = new ConversationRunContext(
                USER_ID,
                "opencode",
                "local-client-protected-run",
                "server-a",
                null,
                session,
                workspace,
                localNode,
                null,
                1,
                NOW.plusSeconds(3600),
                RuntimeKind.LOCAL_CLIENT,
                localNode.localClientInstanceId(),
                localNode.connectionGeneration());

        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        com.enterprise.testagent.domain.session.SessionRepository sessions =
                mock(com.enterprise.testagent.domain.session.SessionRepository.class);
        RunRepository runs = mock(RunRepository.class);
        SessionMessageRepository messages = mock(SessionMessageRepository.class);
        ExecutionNodeRepository nodes = mock(ExecutionNodeRepository.class);
        RoutingDecisionRepository routing = mock(RoutingDecisionRepository.class);
        AgentSessionBindingRepository bindings = mock(AgentSessionBindingRepository.class);
        RunEventAppender events = mock(RunEventAppender.class);
        ConversationRunContextResolver contextResolver = mock(ConversationRunContextResolver.class);
        ProtectedAgentExecutionService protectedExecution = mock(ProtectedAgentExecutionService.class);
        AgentRuntime protectedRuntime = mock(AgentRuntime.class);

        when(workspaces.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(runs.save(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodes.findRoutableNodes(anyInt())).thenReturn(List.of(localNode, serverNode));
        when(nodes.findById(serverNode.executionNodeId())).thenReturn(Optional.of(serverNode));
        when(routing.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bindings.findBySessionIdAndAgentId(SESSION_ID, ProtectedOpencodeAgentRuntime.AGENT_ID))
                .thenReturn(Optional.empty());
        when(bindings.save(any(AgentSessionBinding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contextResolver.resolve(USER_ID, "opencode", input, "trace_protected_run"))
                .thenReturn(Optional.of(localContext));
        when(protectedRuntime.agentId()).thenReturn(ProtectedOpencodeAgentRuntime.AGENT_ID);
        when(protectedRuntime.createDispatchMessageId()).thenReturn("msg_protectedrun1234567890");
        when(protectedRuntime.createSession(any())).thenReturn(Mono.just(
                new AgentCreateSessionResult("ses_remoteprotected1234567890")));
        when(protectedRuntime.openRunEventStream(any())).thenReturn(
                new AgentEventStream(Mono.empty(), Flux.never()));
        when(protectedRuntime.startRun(any())).thenReturn(Mono.just(new AgentStartRunResult(true)));
        ProtectedAgentExecutionService.ExecutionContext protectedContext = new ProtectedAgentExecutionService.ExecutionContext(
                definition(),
                "/srv/test-agent/protected/ses_protectedrun1234567890",
                "protected system prompt",
                Map.of(
                        "runtimeAgentId", ProtectedOpencodeAgentRuntime.AGENT_ID,
                        "revisionId", "hub_rev_agent_1",
                        "contentSha256", "sha256-agent"),
                "pag_secret",
                Map.of("bash", false, "local_files_read_file", true));
        when(protectedExecution.prepare(eq(USER_ID), any(Run.class), eq(session), eq(workspace),
                        eq("protected:hub_rev_agent_1")))
                .thenReturn(protectedContext);

        RunApplicationService service = new RunApplicationService(
                workspaces,
                sessions,
                runs,
                messages,
                nodes,
                routing,
                events,
                new AgentRuntimeRegistry(List.of(protectedRuntime)),
                bindings,
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                null,
                ManagedWorkspacePathResolver.legacyOnly(),
                mock(RunSessionMessageSnapshotService.class),
                null,
                null,
                null,
                contextResolver);
        service.setProtectedAgentExecutionService(protectedExecution);

        Run run = service.startRun(USER_ID, "opencode", input, "trace_protected_run");

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(run.agentId()).isEqualTo(ProtectedOpencodeAgentRuntime.AGENT_ID);
        verify(protectedExecution).configureMcp(
                eq(protectedContext), any(AgentRuntime.class), eq(serverNode), eq("trace_protected_run"));
        ArgumentCaptor<AgentStartRunCommand> dispatch = ArgumentCaptor.forClass(AgentStartRunCommand.class);
        verify(protectedRuntime, timeout(1000)).startRun(dispatch.capture());
        assertThat(dispatch.getValue().node().runtimeKind()).isEqualTo(RuntimeKind.SERVER_PROCESS);
        assertThat(dispatch.getValue().directory()).isEqualTo(protectedContext.serverDirectory());
        assertThat(dispatch.getValue().system()).contains("protected system prompt");
        assertThat(dispatch.getValue().tools())
                .containsEntry("bash", false)
                .containsEntry("local_files_read_file", true);
        assertThat(dispatch.getValue().directory()).doesNotContain("/Users/test/workspace");

        ArgumentCaptor<RunEventDraft> eventDraft = ArgumentCaptor.forClass(RunEventDraft.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).append(
                eventDraft.capture(),
                eq(com.enterprise.testagent.domain.run.RunStorageMode.LEGACY_FULL),
                org.mockito.ArgumentMatchers.isNull());
        RunEventDraft created = eventDraft.getAllValues().stream()
                .filter(event -> event.type() == RunEventType.RUN_CREATED)
                .findFirst()
                .orElseThrow();
        assertThat(created.payload().get("protectedAgent").toString())
                .contains("hub_rev_agent_1", "sha256-agent")
                .doesNotContain("protected system prompt", "pag_secret");
    }

    private static ProtectedAgentDefinitionResolver.Definition definition() {
        return new ProtectedAgentDefinitionResolver.Definition(
                "hub_agent_1",
                "protected-review",
                "hub_rev_agent_1",
                "artifact-agent",
                "sha256-agent",
                "Protected Review",
                Map.of("AGENT.md", "agent instructions"),
                List.of());
    }

    private static ExecutionNode localNode() {
        return new ExecutionNode(
                new ExecutionNodeId("node_localprotectedrun1234567890"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                NOW,
                Set.of("chat", "file-management"),
                NOW,
                NOW,
                "trace_protected_run",
                RuntimeKind.LOCAL_CLIENT,
                "lci_protectedrun1234567890",
                7L);
    }

    private static ExecutionNode serverNode() {
        return new ExecutionNode(
                new ExecutionNodeId("node_serverprotectedrun1234567890"),
                "http://127.0.0.1:5096",
                ExecutionNodeStatus.READY,
                0,
                4,
                100,
                NOW,
                Set.of("chat"),
                NOW,
                NOW,
                "trace_protected_run");
    }
}
