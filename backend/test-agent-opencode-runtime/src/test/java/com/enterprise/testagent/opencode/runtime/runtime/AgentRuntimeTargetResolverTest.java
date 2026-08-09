package com.enterprise.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AgentRuntimeTargetResolverTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void experienceSessionCreationFailureDoesNotRetainPhysicalDirectoryOrCause() {
        String physicalRoot = "/srv/private/experience-root";
        WorkspaceId workspaceId = new WorkspaceId("wrk_exp_runtime_session");
        Session session = new Session(
                new SessionId("ses_experience_runtime"),
                workspaceId,
                "体验会话",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_experience_runtime");
        Workspace workspace = new Workspace(
                workspaceId,
                "体验工作区",
                physicalRoot,
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "server-a",
                "trace_experience_runtime");
        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_experience_runtime"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                NOW,
                Set.of("opencode"),
                NOW,
                NOW,
                "trace_experience_runtime");
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.createSession(any())).thenReturn(Mono.error(new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "POST /session?directory=" + physicalRoot,
                Map.of("directory", physicalRoot),
                new IllegalStateException("failed at " + physicalRoot))));
        AgentRuntimeRegistry registry = mock(AgentRuntimeRegistry.class);
        when(registry.normalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        AgentSessionBindingRepository bindings = mock(AgentSessionBindingRepository.class);
        when(bindings.findBySessionIdAndAgentId(session.sessionId(), "opencode")).thenReturn(Optional.empty());
        AgentRuntimeTargetResolver resolver = new AgentRuntimeTargetResolver(
                mock(WorkspaceRepository.class),
                mock(SessionRepository.class),
                mock(ExecutionNodeRepository.class),
                registry,
                bindings,
                null,
                ManagedWorkspacePathResolver.legacyOnly(),
                mock(ConversationWorkspaceAccessAuthorizer.class));

        assertThatThrownBy(() -> resolver.ensureAgentSession(
                        "opencode", runtime, session, workspace, node, "trace_experience_runtime"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("体验工作区远端会话创建失败");
                    assertThat(exception.details().toString()).doesNotContain(physicalRoot);
                    assertThat(exception).hasNoCause();
                });
    }
}
