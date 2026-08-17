package com.enterprise.testagent.opencode.runtime.protectedagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.hub.ProtectedAgentDefinitionResolver;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientWorkspaceFileGateway;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProtectedAgentMcpServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_protected1234567890");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_protected1234567890");
    private static final RunId RUN_ID = new RunId("run_protected1234567890");
    private static final LocalClientInstanceId CLIENT_ID = new LocalClientInstanceId("lci_protected1234567890");

    @Test
    void initializesAndForwardsReadThroughFencedLocalFileGateway() {
        Fixture fixture = new Fixture();
        ProtectedAgentFileGrantService.IssuedGrant issued = fixture.issue();
        JsonNode initialize = fixture.mcp.handle(
                "Bearer " + issued.token(),
                fixture.json("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"),
                "trace_mcp");
        JsonNode read = fixture.mcp.handle(
                "Bearer " + issued.token(),
                fixture.json("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"read_file\",\"arguments\":{\"path\":\"README.md\"}}}"),
                "trace_mcp");

        assertThat(initialize.path("result").path("protocolVersion").asText()).isEqualTo("2025-03-26");
        assertThat(read.path("result").path("isError").asBoolean()).isFalse();
        assertThat(read.toString()).contains("hello");
        verify(fixture.fileGateway).invoke(
                eq(CLIENT_ID.value()), eq(7L), eq(WORKSPACE_ID.value()), eq("root-sha256"),
                eq("workspace.read"), any(JsonNode.class), eq("trace_mcp"));
    }

    @Test
    void connectionGenerationChangeInvalidatesGrantBeforeFileRpc() {
        Fixture fixture = new Fixture();
        ProtectedAgentFileGrantService.IssuedGrant issued = fixture.issue();
        when(fixture.connections.find(CLIENT_ID)).thenReturn(Optional.of(fixture.route(8)));

        assertThatThrownBy(() -> fixture.mcp.handle(
                        "Bearer " + issued.token(),
                        fixture.json("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"),
                        "trace_mcp"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void skillResourcesStayOnServerGrantAndDoNotUseLocalFileRpc() {
        Fixture fixture = new Fixture();
        ProtectedAgentFileGrantService.IssuedGrant issued = fixture.issue();

        JsonNode response = fixture.mcp.handle(
                "Bearer " + issued.token(),
                fixture.json("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"read_skill_resource\","
                        + "\"arguments\":{\"path\":\"skills/review/references/checklist.md\"}}}"),
                "trace_mcp");

        assertThat(response.toString()).contains("server-only checklist");
    }

    private static final class Fixture {
        private final LocalClientWorkspaceRepository workspaces = mock(LocalClientWorkspaceRepository.class);
        private final LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        private final RunRepository runs = mock(RunRepository.class);
        private final LocalClientWorkspaceFileGateway fileGateway = mock(LocalClientWorkspaceFileGateway.class);
        private final BackendJavaRouteResolver backendRoutes = mock(BackendJavaRouteResolver.class);
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final ProtectedAgentFileGrantService grants = new ProtectedAgentFileGrantService(
                workspaces, connections, runs, fileGateway, backendRoutes, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        private final ProtectedAgentMcpService mcp = new ProtectedAgentMcpService(grants, objectMapper);

        private Fixture() {
            LocalClientWorkspaceBinding binding = new LocalClientWorkspaceBinding(
                    WORKSPACE_ID, USER_ID, CLIENT_ID, "/Users/test/workspace", "root-sha256", "fs-id", NOW, NOW);
            Run run = new Run(
                    RUN_ID,
                    new SessionId("ses_protected1234567890"),
                    WORKSPACE_ID,
                    RunStatus.PENDING,
                    NOW,
                    NOW,
                    "trace_mcp").withSource(ConversationSourceType.MANUAL, null, USER_ID);
            when(workspaces.findByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.of(binding));
            when(connections.find(CLIENT_ID)).thenReturn(Optional.of(route(7)));
            when(runs.findById(RUN_ID)).thenReturn(Optional.of(run));
            when(backendRoutes.isCurrent(any(BackendProcessId.class))).thenReturn(true);
            when(fileGateway.invoke(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any(), any(), any(), any()))
                    .thenReturn(JsonNodeFactory.instance.objectNode().put("content", "hello"));
        }

        private ProtectedAgentFileGrantService.IssuedGrant issue() {
            ProtectedAgentDefinitionResolver.SkillDefinition skill = new ProtectedAgentDefinitionResolver.SkillDefinition(
                    "hub_skill", "review", "hub_rev_skill", "artifact-skill", "content-skill", "review",
                    Map.of("SKILL.md", "skill", "references/checklist.md", "server-only checklist"));
            ProtectedAgentDefinitionResolver.Definition definition = new ProtectedAgentDefinitionResolver.Definition(
                    "hub_agent", "protected", "hub_rev_agent", "artifact-agent", "content-agent", "protected",
                    Map.of("AGENT.md", "agent"), List.of(skill));
            return grants.issue(USER_ID, RUN_ID, WORKSPACE_ID, definition);
        }

        private LocalClientConnectionRoute route(long generation) {
            return new LocalClientConnectionRoute(
                    CLIENT_ID,
                    USER_ID,
                    new BackendProcessId("bjp_protected1234567890"),
                    generation,
                    "127.0.0.1",
                    List.of("127.0.0.1"),
                    4096,
                    LocalClientProcessStatus.RUNNING,
                    123L,
                    NOW,
                    true,
                    NOW,
                    NOW);
        }

        private JsonNode json(String value) {
            try {
                return objectMapper.readTree(value);
            } catch (Exception exception) {
                throw new IllegalArgumentException(exception);
            }
        }
    }
}
