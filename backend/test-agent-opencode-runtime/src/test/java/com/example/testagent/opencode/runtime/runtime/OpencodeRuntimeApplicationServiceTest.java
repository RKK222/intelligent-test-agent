package com.example.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeRepository;
import com.example.testagent.domain.node.ExecutionNodeStatus;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionRepository;
import com.example.testagent.domain.session.SessionStatus;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.domain.workspace.WorkspaceRepository;
import com.example.testagent.domain.workspace.WorkspaceStatus;
import com.example.testagent.opencode.client.OpencodeClientFacade;
import com.example.testagent.opencode.client.OpencodeRuntimeCommand;
import com.example.testagent.opencode.client.OpencodeRuntimeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class OpencodeRuntimeApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listAgentsUsesWorkspaceDirectoryAndV2AgentPath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(List.of(Map.of("id", "build"))))));

        Object result = fixture.service.listAgents("wrk_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/api/agent");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        assertThat(result).isInstanceOf(List.class);
    }

    @Test
    void listProvidersUsesV2ProviderPath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("data", List.of(Map.of("id", "anthropic")))))));

        fixture.service.listProviders("wrk_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/api/provider");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
    }

    @Test
    void mcpToolsUsesExperimentalToolListWhenModelSelected() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(List.of(Map.of("id", "bash"))))));

        fixture.service.mcpTools("wrk_1234567890abcdef", "anthropic", "claude-sonnet", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/experimental/tool");
        assertThat(command.query()).containsEntry("provider", "anthropic").containsEntry("model", "claude-sonnet");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
    }

    @Test
    void mcpResourcesUsesExperimentalResourcePath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(List.of(Map.of("uri", "file:///tmp/demo/README.md"))))));

        fixture.service.mcpResources("wrk_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/experimental/resource");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
    }

    @Test
    void getConfigUsesGlobalConfigPath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("theme", "dark")))));

        fixture.service.getConfig("wrk_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/global/config");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
    }

    @Test
    void authorizeProviderOAuthUsesProviderOAuthPathAndBody() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("url", "https://auth.example")))));

        fixture.service.authorizeProviderOAuth("anthropic", Map.of("callbackUrl", "http://localhost/callback"), "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/provider/anthropic/oauth/authorize");
        assertThat(command.body()).isEqualTo(Map.of("callbackUrl", "http://localhost/callback"));
    }

    @Test
    void createWorktreeUsesExperimentalWorktreePath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("path", "/tmp/demo/.worktrees/feature")))));

        fixture.service.createWorktree(Map.of("workspaceId", "wrk_1234567890abcdef", "branch", "feature"), "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/experimental/worktree");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
    }

    @Test
    void shareSessionUsesRemoteSessionId() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("url", "https://opencode.ai/s/abc")))));

        fixture.service.shareSession("ses_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/session/ses_remote1234567890abcdef/share");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
    }

    @Test
    void startMcpAuthUsesMcpAuthPath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("state", "pending")))));

        fixture.service.startMcpAuth("github", Map.of("callbackUrl", "http://localhost/callback"), "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/mcp/github/auth");
        assertThat(command.body()).isEqualTo(Map.of("callbackUrl", "http://localhost/callback"));
    }

    @Test
    void replyPermissionUsesRemoteSessionIdAndRequestBody() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("accepted", true)))));

        fixture.service.replyPermission(
                "ses_1234567890abcdef",
                "req_1",
                Map.of("decision", "once"),
                "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/permission/req_1/reply");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        assertThat(command.body()).isEqualTo(Map.of("reply", "once"));
    }

    private static final class Fixture {
        private final WorkspaceRepository workspaceRepository = org.mockito.Mockito.mock(WorkspaceRepository.class);
        private final SessionRepository sessionRepository = org.mockito.Mockito.mock(SessionRepository.class);
        private final ExecutionNodeRepository executionNodeRepository = org.mockito.Mockito.mock(ExecutionNodeRepository.class);
        private final OpencodeClientFacade facade = org.mockito.Mockito.mock(OpencodeClientFacade.class);
        private final OpencodeRuntimeApplicationService service = new OpencodeRuntimeApplicationService(
                workspaceRepository,
                sessionRepository,
                executionNodeRepository,
                facade,
                new ObjectMapper());

        private Fixture() {
            when(workspaceRepository.findById(new WorkspaceId("wrk_1234567890abcdef"))).thenReturn(Optional.of(workspace()));
            when(sessionRepository.findById(new SessionId("ses_1234567890abcdef"))).thenReturn(Optional.of(session()));
            when(executionNodeRepository.findRoutableNodes(1)).thenReturn(List.of(node()));
            when(executionNodeRepository.findById(new ExecutionNodeId("node_1234567890abcdef"))).thenReturn(Optional.of(node()));
        }

        private OpencodeRuntimeCommand captureCommand() {
            ArgumentCaptor<OpencodeRuntimeCommand> captor = ArgumentCaptor.forClass(OpencodeRuntimeCommand.class);
            verify(facade).runtime(captor.capture());
            return captor.getValue();
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
                    new WorkspaceId("wrk_1234567890abcdef"),
                    "Demo",
                    SessionStatus.ACTIVE,
                    NOW,
                    NOW,
                    "trace_1234567890abcdef",
                    "ses_remote1234567890abcdef",
                    new ExecutionNodeId("node_1234567890abcdef"));
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
    }
}
