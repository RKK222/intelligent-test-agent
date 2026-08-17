package com.enterprise.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionRuntimeTargetRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentRuntimeTargetResolverLocalClientTest {

    private static final Instant NOW = Instant.parse("2026-08-12T03:00:00Z");

    @Test
    void shouldPersistOfflineForeignKeyAnchorButReturnLiveLocalNode() {
        WorkspaceId workspaceId = new WorkspaceId("wrk_localruntime1234567890");
        UserId userId = new UserId("usr_localruntime1234567890");
        LocalClientInstanceId clientInstanceId = new LocalClientInstanceId("lci_localruntime1234567890");
        BackendProcessId backendProcessId = new BackendProcessId("bjp_localruntime1234567890");

        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        ExecutionNodeRepository nodes = mock(ExecutionNodeRepository.class);
        AgentSessionBindingRepository agentBindings = mock(AgentSessionBindingRepository.class);
        LocalClientWorkspaceRepository localWorkspaces = mock(LocalClientWorkspaceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        SessionRuntimeTargetRepository sessionTargets = mock(SessionRuntimeTargetRepository.class);
        BackendJavaRouteResolver backendRoutes = mock(BackendJavaRouteResolver.class);
        AgentRuntime runtime = mock(AgentRuntime.class);

        Workspace workspace = new Workspace(workspaceId, "local", "/Users/test/workspace", NOW);
        LocalClientWorkspaceBinding localBinding = new LocalClientWorkspaceBinding(
                workspaceId,
                userId,
                clientInstanceId,
                "/Users/test/workspace",
                "sha256-root",
                "file-key",
                NOW,
                NOW);
        LocalClientConnectionRoute connection = new LocalClientConnectionRoute(
                clientInstanceId,
                userId,
                backendProcessId,
                7,
                "127.0.0.1",
                List.of("192.0.2.10"),
                4096,
                LocalClientProcessStatus.RUNNING,
                1234L,
                NOW,
                true,
                NOW,
                NOW.plusSeconds(5));

        when(runtime.agentId()).thenReturn("opencode");
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(localWorkspaces.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(localBinding));
        when(connections.find(clientInstanceId)).thenReturn(Optional.of(connection));
        when(backendRoutes.isCurrent(backendProcessId)).thenReturn(true);

        AgentRuntimeTargetResolver resolver = new AgentRuntimeTargetResolver(
                workspaces,
                sessions,
                nodes,
                new AgentRuntimeRegistry(List.of(runtime)),
                agentBindings,
                null);
        resolver.configureLocalClientRuntime(localWorkspaces, connections, sessionTargets, backendRoutes);

        AgentRuntimeTargetResolver.WorkspaceRuntimeTarget target =
                resolver.workspaceTarget("opencode", userId, workspaceId.value(), "trace_local_runtime");

        assertThat(target.directory()).isEqualTo("/Users/test/workspace");
        assertThat(target.workspaceId()).isEqualTo(workspaceId);
        assertThat(target.node().runtimeKind()).isEqualTo(RuntimeKind.LOCAL_CLIENT);
        assertThat(target.node().localClientInstanceId()).isEqualTo(clientInstanceId.value());
        assertThat(target.node().connectionGeneration()).isEqualTo(7L);
        assertThat(target.node().status()).isEqualTo(ExecutionNodeStatus.READY);

        ArgumentCaptor<ExecutionNode> anchorCaptor = ArgumentCaptor.forClass(ExecutionNode.class);
        verify(nodes).save(anchorCaptor.capture());
        ExecutionNode anchor = anchorCaptor.getValue();
        assertThat(anchor.executionNodeId()).isEqualTo(target.node().executionNodeId());
        assertThat(anchor.runtimeKind()).isEqualTo(RuntimeKind.SERVER_PROCESS);
        assertThat(anchor.status()).isEqualTo(ExecutionNodeStatus.OFFLINE);
        assertThat(anchor.capabilities()).containsExactly("local-client-anchor");
    }
}
