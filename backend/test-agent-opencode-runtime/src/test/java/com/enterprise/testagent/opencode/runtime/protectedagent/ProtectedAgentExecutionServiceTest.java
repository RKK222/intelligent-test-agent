package com.enterprise.testagent.opencode.runtime.protectedagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.domain.hub.ProtectedAgentDefinitionResolver;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProxyRuntimeSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class ProtectedAgentExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_execution1234567890");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_execution1234567890");
    private static final SessionId SESSION_ID = new SessionId("ses_execution1234567890");

    @Test
    void freezesDefinitionInServerDirectoryAndConfiguresAuthenticatedMcp(@TempDir Path tempDir) throws Exception {
        ProtectedAgentDefinitionResolver definitions = mock(ProtectedAgentDefinitionResolver.class);
        ProtectedAgentFileGrantService grants = mock(ProtectedAgentFileGrantService.class);
        InternalModelProxyRuntimeSettings backendSettings = mock(InternalModelProxyRuntimeSettings.class);
        ProtectedAgentDefinitionResolver.Definition definition = definition();
        when(definitions.resolve(USER_ID, WORKSPACE_ID, "hub_rev_agent_1")).thenReturn(definition);
        when(grants.issue(eq(USER_ID), any(RunId.class), eq(WORKSPACE_ID), eq(definition)))
                .thenReturn(new ProtectedAgentFileGrantService.IssuedGrant("pag_runtime_secret", null));
        when(backendSettings.sameNodeBaseUrl()).thenReturn("http://127.0.0.1:8080");
        ProtectedAgentExecutionService service = new ProtectedAgentExecutionService(
                definitions, grants, backendSettings, tempDir.toString());
        Run run = new Run(
                new RunId("run_execution1234567890"), SESSION_ID, WORKSPACE_ID,
                RunStatus.PENDING, NOW, NOW, "trace_execution");
        Session session = new Session(SESSION_ID, WORKSPACE_ID, "protected", NOW);
        Workspace workspace = new Workspace(WORKSPACE_ID, "local", "/Users/test/workspace", NOW);

        ProtectedAgentExecutionService.ExecutionContext context = service.prepare(
                USER_ID, run, session, workspace, "protected:hub_rev_agent_1");

        Path serverDirectory = Path.of(context.serverDirectory());
        assertThat(serverDirectory).startsWith(tempDir.toRealPath());
        assertThat(serverDirectory).exists().isDirectory();
        assertThat(Files.getPosixFilePermissions(serverDirectory)).containsExactlyInAnyOrder(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
        assertThat(context.systemPrompt())
                .contains("agent instructions", "local_files_list_skill_resources", "review",
                        "hub_rev_agent_1", "hub_rev_skill_1")
                .doesNotContain("skill instructions", "/Users/test/workspace");
        assertThat(context.auditPayload().toString())
                .contains("hub_rev_agent_1", "sha256-agent", "hub_rev_skill_1", "sha256-skill")
                .doesNotContain("agent instructions", "skill instructions", "pag_runtime_secret");
        assertThat(context.toolPermissions())
                .containsEntry("bash", false)
                .containsEntry("read", false)
                .containsEntry("local_files_read_file", true)
                .doesNotContainKey("local_files_write_file");

        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.runtime(any())).thenReturn(Mono.just(new AgentRuntimeResult(
                new ObjectMapper().valueToTree(Map.of(
                        "local_files", Map.of("status", "connected"))))));
        service.configureMcp(context, runtime, serverNode(), "trace_execution");

        ArgumentCaptor<AgentRuntimeCommand> commandCaptor = ArgumentCaptor.forClass(AgentRuntimeCommand.class);
        verify(runtime).runtime(commandCaptor.capture());
        AgentRuntimeCommand command = commandCaptor.getValue();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/mcp");
        assertThat(command.directory()).isEqualTo(context.serverDirectory());
        assertThat(command.body().toString())
                .contains("http://127.0.0.1:8080/api/internal/platform/protected-agent/mcp")
                .contains("Bearer pag_runtime_secret")
                .doesNotContain("agent instructions", "skill instructions");
    }

    private static ProtectedAgentDefinitionResolver.Definition definition() {
        ProtectedAgentDefinitionResolver.SkillDefinition skill =
                new ProtectedAgentDefinitionResolver.SkillDefinition(
                        "hub_skill_1", "review", "hub_rev_skill_1", "artifact-skill", "sha256-skill",
                        "Review", Map.of("SKILL.md", "skill instructions"));
        return new ProtectedAgentDefinitionResolver.Definition(
                "hub_agent_1", "protected-review", "hub_rev_agent_1", "artifact-agent", "sha256-agent",
                "Protected Review", Map.of("AGENT.md", "agent instructions"), List.of(skill));
    }

    private static ExecutionNode serverNode() {
        return new ExecutionNode(
                new ExecutionNodeId("node_execution1234567890"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                100,
                NOW,
                Set.of("chat"),
                NOW,
                NOW,
                "trace_execution");
    }
}
