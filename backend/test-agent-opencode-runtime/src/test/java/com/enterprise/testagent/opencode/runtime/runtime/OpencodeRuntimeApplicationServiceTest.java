package com.enterprise.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.agent.runtime.OpencodeAgentRuntime;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigMessageGate;
import com.enterprise.testagent.domain.hub.ProtectedAgentDefinitionResolver;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityRepository;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.session.SessionApplicationService;
import com.enterprise.testagent.opencode.runtime.session.UserRuntimeDisposeCoordinator;
import com.enterprise.testagent.opencode.client.OpencodeClientFacade;
import com.enterprise.testagent.opencode.client.OpencodeCreateSessionCommand;
import com.enterprise.testagent.opencode.client.OpencodeCreateSessionResult;
import com.enterprise.testagent.opencode.client.OpencodeRuntimeCommand;
import com.enterprise.testagent.opencode.client.OpencodeRuntimeResult;
import com.enterprise.testagent.opencode.client.OpencodeSessionExistsCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    void compactSessionTouchesPlatformRevisionOnlyAfterRemoteSuccess() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("ok", true)))));

        Object result = fixture.service.compactSession(
                "ses_1234567890abcdef",
                Map.of("providerID", "openai", "modelID", "gpt-5"),
                "trace_1234567890abcdef");

        assertThat(result).isEqualTo(Map.of("ok", true));
        assertThat(fixture.captureCommand().path())
                .isEqualTo("/session/ses_remote1234567890abcdef/summarize");
        verify(fixture.sessionApplicationService).touchSession(
                new SessionId("ses_1234567890abcdef"), "trace_1234567890abcdef");
    }

    @Test
    void compactSessionDoesNotAdvanceRevisionWhenRemoteSummarizeFails() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.error(new PlatformException(
                ErrorCode.OPENCODE_BAD_GATEWAY, "summarize failed")));

        assertThatThrownBy(() -> fixture.service.compactSession(
                        "ses_1234567890abcdef", Map.of(), "trace_1234567890abcdef"))
                .isInstanceOf(PlatformException.class);

        verify(fixture.sessionApplicationService, never()).touchSession(any(), anyString());
    }

    @Test
    void legacySideQuestionUsesSamePublicConfigGate() {
        Fixture fixture = new Fixture();
        UserId userId = new UserId("usr_1234567890abcdef");
        PublicAgentConfigMessageGate gate = ignored ->
                PublicAgentConfigMessageGate.MessageGateStatus.blocked("acr_active");
        fixture.service.configurePublicConfigMessageGate(gate);

        assertThatThrownBy(() -> fixture.service.withUser(
                        userId,
                        () -> fixture.service.sideQuestion(
                                "ses_1234567890abcdef",
                                new SideQuestionInput("问题", null, null, "provider/model"),
                                "trace_1234567890abcdef")))
                .isInstanceOf(PlatformException.class);

        verify(fixture.facade, never()).runtime(any());
    }

    @Test
    void legacyMessageEntrypointsUseSameUserDisposeGate() {
        Fixture fixture = new Fixture();
        UserId userId = new UserId("usr_1234567890abcdef");
        UserRuntimeDisposeCoordinator coordinator = org.mockito.Mockito.mock(UserRuntimeDisposeCoordinator.class);
        doThrow(new PlatformException(ErrorCode.CONFLICT, "运行态正在释放"))
                .when(coordinator)
                .requireNotDisposing(userId, "trace_1234567890abcdef");
        fixture.service.configureUserRuntimeDisposeCoordinator(coordinator);

        assertThatThrownBy(() -> fixture.service.withUser(
                        userId,
                        () -> fixture.service.sideQuestion(
                                "ses_1234567890abcdef",
                                new SideQuestionInput("问题", null, null, "provider/model"),
                                "trace_1234567890abcdef")))
                .isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> fixture.service.withUser(
                        userId,
                        () -> fixture.service.commandSession(
                                "ses_1234567890abcdef",
                                Map.of("command", "test"),
                                "trace_1234567890abcdef")))
                .isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> fixture.service.withUser(
                        userId,
                        () -> fixture.service.shellSession(
                                "ses_1234567890abcdef",
                                Map.of("command", "pwd"),
                                "trace_1234567890abcdef")))
                .isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> fixture.service.withAgent(
                        "custom-agent",
                        userId,
                        () -> fixture.service.sideQuestion(
                                "ses_1234567890abcdef",
                                new SideQuestionInput("自定义 Agent 问题", null, null, "provider/model"),
                                "trace_1234567890abcdef")))
                .isInstanceOf(PlatformException.class);

        verify(coordinator, org.mockito.Mockito.times(4))
                .requireNotDisposing(userId, "trace_1234567890abcdef");
        verify(fixture.facade, never()).runtime(any());
    }

    @Test
    void listAgentsUsesWorkspaceDirectoryAndAgentPath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(List.of(Map.of("id", "build"))))));

        Object result = fixture.service.listAgents("wrk_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/agent");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        assertThat(result).isInstanceOf(List.class);
    }

    @Test
    void listAgentsUnwrapsV2LocationCatalogBeforeReturningPlatformAgents() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of(
                        "location", Map.of("directory", "/tmp/demo"),
                        "data", List.of(Map.of("id", "build", "name", "Build")))))));

        assertThat(fixture.service.listAgents("wrk_1234567890abcdef", "trace_1234567890abcdef"))
                .isEqualTo(List.of(Map.of("id", "build", "name", "Build")));
    }

    @Test
    void authenticatedLocalWorkspaceCatalogAddsOnlyOpaqueProtectedSelections() {
        UserId userId = new UserId("usr_protectedcatalog1234567890");
        WorkspaceId workspaceId = new WorkspaceId("wrk_protectedcatalog1234567890");
        AgentRuntime runtime = org.mockito.Mockito.mock(AgentRuntime.class);
        AgentRuntimeTargetResolver targetResolver = org.mockito.Mockito.mock(AgentRuntimeTargetResolver.class);
        ProtectedAgentDefinitionResolver definitions = org.mockito.Mockito.mock(ProtectedAgentDefinitionResolver.class);
        when(runtime.agentId()).thenReturn("opencode");
        when(runtime.runtime(any())).thenReturn(Mono.just(new AgentRuntimeResult(
                objectMapper.valueToTree(List.of(Map.of("id", "build", "name", "Build"))))));
        when(targetResolver.workspaceTarget("opencode", userId, workspaceId.value(), "trace_protected_catalog"))
                .thenReturn(new AgentRuntimeTargetResolver.WorkspaceRuntimeTarget(
                        runtime,
                        localClientNode(),
                        "/Users/test/workspace",
                        workspaceId));
        when(definitions.listCatalog(userId, workspaceId)).thenReturn(List.of(
                new ProtectedAgentDefinitionResolver.CatalogItem(
                        "protected:hub_rev_agent_1",
                        "hub_rev_agent_1",
                        "合规审查",
                        "服务器执行",
                        "sha256-agent")));
        OpencodeRuntimeApplicationService service = new OpencodeRuntimeApplicationService(
                new AgentRuntimeRegistry(List.of(runtime)), targetResolver, objectMapper, null);
        service.configureProtectedAgentDefinitionResolver(definitions);

        Object result = service.withUser(
                userId,
                () -> service.listAgents(workspaceId.value(), "trace_protected_catalog"));

        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).hasSize(2);
        assertThat((List<?>) result).anySatisfy(item -> assertThat(item)
                .isEqualTo(Map.of(
                        "id", "protected:hub_rev_agent_1",
                        "agentId", "protected:hub_rev_agent_1",
                        "name", "合规审查",
                        "mode", "primary",
                                "description", "服务器执行",
                                "protected", true,
                                "source", "APPLICATION_HUB",
                                "revisionId", "hub_rev_agent_1",
                        "contentSha256", "sha256-agent")));
        assertThat(result.toString()).doesNotContain("AGENT.md", "系统提示词", "SKILL.md");
    }

    @Test
    void localPublicCapabilityHidesPublicGitDuplicateButKeepsApplicationHubAgent() {
        UserId userId = new UserId("usr_publiccapcatalog1234567890");
        WorkspaceId workspaceId = new WorkspaceId("wrk_publiccapcatalog1234567890");
        LocalClientInstanceId instanceId = new LocalClientInstanceId("lci_protectedcatalog1234567890");
        AgentRuntime runtime = org.mockito.Mockito.mock(AgentRuntime.class);
        AgentRuntimeTargetResolver targetResolver = org.mockito.Mockito.mock(AgentRuntimeTargetResolver.class);
        ProtectedAgentDefinitionResolver definitions = org.mockito.Mockito.mock(ProtectedAgentDefinitionResolver.class);
        LocalClientInstanceRepository instances = org.mockito.Mockito.mock(LocalClientInstanceRepository.class);
        LocalClientPublicCapabilityRepository capabilities =
                org.mockito.Mockito.mock(LocalClientPublicCapabilityRepository.class);
        when(runtime.agentId()).thenReturn("opencode");
        when(runtime.runtime(any())).thenReturn(Mono.just(new AgentRuntimeResult(
                objectMapper.valueToTree(List.of(Map.of("id", "public-agent", "name", "本地公共 Agent"))))));
        when(targetResolver.workspaceTarget("opencode", userId, workspaceId.value(), "trace_public_capability"))
                .thenReturn(new AgentRuntimeTargetResolver.WorkspaceRuntimeTarget(
                        runtime, localClientNode(), "/Users/test/workspace", workspaceId));
        when(definitions.listCatalog(userId, workspaceId)).thenReturn(List.of(
                new ProtectedAgentDefinitionResolver.CatalogItem(
                        "protected:public-agent", "public-agent", "公共 Agent", "公共 Git",
                        "sha256-public", ProtectedAgentDefinitionResolver.CatalogSource.PUBLIC_GIT),
                new ProtectedAgentDefinitionResolver.CatalogItem(
                        "protected:hub-agent", "hub-agent", "Hub Agent", "应用 Hub",
                        "sha256-hub", ProtectedAgentDefinitionResolver.CatalogSource.APPLICATION_HUB)));
        when(instances.findById(instanceId)).thenReturn(Optional.of(new LocalClientInstance(
                instanceId, userId, "Mac 客户端", "macos", "aarch64", "1.0.0", "2.0.18", "1.0.0",
                List.of("PUBLIC_CAPABILITY_SYNC_V1"), true, null, null, null,
                NOW, NOW, NOW, null)));
        when(capabilities.findInstanceState(instanceId)).thenReturn(Optional.of(
                new LocalClientPublicCapabilityModels.InstanceState(
                        instanceId,
                        "a".repeat(40),
                        "b".repeat(64),
                        null,
                        null,
                        LocalClientPublicCapabilityModels.InstanceStatus.CURRENT,
                        null,
                        NOW,
                        NOW)));
        OpencodeRuntimeApplicationService service = new OpencodeRuntimeApplicationService(
                new AgentRuntimeRegistry(List.of(runtime)), targetResolver, objectMapper, null);
        service.configureProtectedAgentDefinitionResolver(definitions);
        service.configureLocalClientInstanceRepository(instances);
        service.configureLocalClientPublicCapabilityRepository(capabilities);

        Object result = service.withUser(
                userId,
                () -> service.listAgents(workspaceId.value(), "trace_public_capability"));

        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).hasSize(2);
        assertThat(result.toString()).contains("本地公共 Agent", "Hub Agent", "APPLICATION_HUB");
        assertThat(result.toString()).doesNotContain("公共 Git", "protected:public-agent");
    }

    @Test
    void listAgentsRequiresCurrentApplicationWorkspaceAccessBeforeCallingRuntime() {
        Fixture fixture = new Fixture();
        UserId userId = new UserId("usr_1234567890abcdef");
        doThrow(new PlatformException(ErrorCode.FORBIDDEN, "当前用户已不是应用有效成员"))
                .when(fixture.workspaceAccessAuthorizer)
                .requireAccess(userId, new WorkspaceId("wrk_1234567890abcdef"));

        assertThatThrownBy(() -> fixture.service.withUser(
                        userId,
                        () -> fixture.service.listAgents(
                                "wrk_1234567890abcdef",
                                "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(fixture.assignmentService, never()).requireReadyProcess(any(), anyString(), anyString());
        verify(fixture.facade, never()).runtime(any());
    }

    @Test
    void sessionRuntimeRejectsAnotherUsersSessionBeforeResolvingProcess() {
        Fixture fixture = new Fixture();
        UserId owner = new UserId("usr_session_owner");
        UserId caller = new UserId("usr_session_caller");
        when(fixture.sessionRepository.findById(new SessionId("ses_1234567890abcdef")))
                .thenReturn(Optional.of(Fixture.session().withSource(
                        com.enterprise.testagent.domain.session.ConversationSourceType.MANUAL,
                        null,
                        owner)));

        assertThatThrownBy(() -> fixture.service.withUser(
                        caller,
                        () -> fixture.service.sessionTodo(
                                "ses_1234567890abcdef",
                                "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(fixture.assignmentService, never()).requireReadyProcess(any(), anyString(), anyString());
        verify(fixture.facade, never()).runtime(any());
    }

    @Test
    void sessionRuntimeRechecksCurrentExperienceWorkspaceAccess() {
        Fixture fixture = new Fixture();
        UserId userId = new UserId("usr_experience_owner");
        fixture.useExperienceSession(userId);
        doThrow(new PlatformException(ErrorCode.FORBIDDEN, "体验资格已失效"))
                .when(fixture.workspaceAccessAuthorizer)
                .requireAccess(userId, new WorkspaceId("wrk_exp_1234567890abcdef"));

        assertThatThrownBy(() -> fixture.service.withUser(
                        userId,
                        () -> fixture.service.sessionTodo(
                                "ses_1234567890abcdef",
                                "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(fixture.assignmentService, never()).requireReadyProcess(any(), anyString(), anyString());
        verify(fixture.facade, never()).runtime(any());
    }

    @Test
    void sessionTodoRestoresLatestV2TodoWritePartWithoutLegacyTodoRoute() {
        Fixture fixture = new Fixture();
        when(fixture.facade.sessionMessages(any())).thenReturn(Mono.just(
                new com.enterprise.testagent.opencode.client.OpencodeSessionMessagesResult(
                        List.of(
                                new com.enterprise.testagent.opencode.client.OpencodeSessionMessage(
                                        Map.of("role", "assistant"),
                                        List.of(Map.of(
                                                "type", "tool",
                                                "toolName", "todowrite",
                                                "input", Map.of("todos", List.of(Map.of(
                                                        "id", "todo_1",
                                                        "content", "迁移 V2",
                                                        "status", "in_progress"))))))),
                        null,
                        null)));

        Object result = fixture.service.sessionTodo(
                "ses_1234567890abcdef",
                "trace_1234567890abcdef");

        assertThat(result).isEqualTo(Map.of(
                "data", List.of(Map.of(
                        "id", "todo_1",
                        "content", "迁移 V2",
                        "status", "in_progress"))));
        verify(fixture.facade).sessionMessages(any());
        verify(fixture.facade, never()).runtime(any());
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
    void listCommandsUsesWorkspaceDirectoryAndCommandPath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(List.of(Map.of("name", "review"))))));

        Object result = fixture.service.listCommands("wrk_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/command");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        assertThat(result).isInstanceOf(List.class);
    }

    @Test
    void runtimeStatusUsesGlobalHealthPath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("healthy", true)))));

        Object result = fixture.service.runtimeStatus("wrk_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/api/info");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        assertThat(result).isInstanceOf(Map.class);
    }

    @Test
    void experienceWorkspaceRejectsLegacyRuntimeFileApisBeforeForwarding() {
        Fixture fixture = new Fixture();
        UserId userId = new UserId("usr_1234567890abcdef");
        fixture.useExperienceWorkspace();
        when(fixture.assignmentService.requireReadyProcess(
                        userId,
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(Fixture.userProcessNode(
                        "node_ocp_1234567890abcdef", "http://10.8.0.12:4096")));
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(List.of(Map.of("path", "/physical/experience/.git/config"))))));

        assertExperienceRuntimeFileApiForbidden(() -> fixture.service.withUser(
                userId,
                () -> fixture.service.fsList(
                        "wrk_exp_1234567890abcdef", ".", "trace_1234567890abcdef")));
        assertExperienceRuntimeFileApiForbidden(() -> fixture.service.withUser(
                userId,
                () -> fixture.service.fsFind(
                        "wrk_exp_1234567890abcdef", "config", "trace_1234567890abcdef")));
        assertExperienceRuntimeFileApiForbidden(() -> fixture.service.withUser(
                userId,
                () -> fixture.service.fsRead(
                        "wrk_exp_1234567890abcdef", ".git/config", "trace_1234567890abcdef")));

        verify(fixture.workspaceAccessAuthorizer, times(3))
                .requireAccess(userId, new WorkspaceId("wrk_exp_1234567890abcdef"));
        verify(fixture.facade, never()).runtime(any());
    }

    @Test
    void experienceWorkspaceRejectsGenericRuntimeVcsBeforeForwarding() {
        Fixture fixture = new Fixture();
        UserId userId = new UserId("usr_1234567890abcdef");
        fixture.useExperienceWorkspace();
        when(fixture.assignmentService.requireReadyProcess(
                        userId,
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(Fixture.userProcessNode(
                        "node_ocp_1234567890abcdef", "http://10.8.0.12:4096")));

        assertExperienceRuntimeFileApiForbidden(() -> fixture.service.withUser(
                userId,
                () -> fixture.service.vcsStatus(
                        "wrk_exp_1234567890abcdef", "trace_1234567890abcdef")));
        assertExperienceRuntimeFileApiForbidden(() -> fixture.service.withUser(
                userId,
                () -> fixture.service.vcsDiff(
                        "wrk_exp_1234567890abcdef", "working", 3, "trace_1234567890abcdef")));

        verify(fixture.workspaceAccessAuthorizer, times(2))
                .requireAccess(userId, new WorkspaceId("wrk_exp_1234567890abcdef"));
        verify(fixture.facade, never()).runtime(any());
    }

    @Test
    void experienceWorkspaceRedactsPhysicalRootFromRuntimeCatalogResponse() {
        Fixture fixture = new Fixture();
        UserId userId = new UserId("usr_1234567890abcdef");
        fixture.useExperienceWorkspace();
        when(fixture.assignmentService.requireReadyProcess(
                        userId,
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(Fixture.userProcessNode(
                        "node_ocp_1234567890abcdef", "http://10.8.0.12:4096")));
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(List.of(Map.of(
                        "name", "review",
                        "template", "Review /physical/experience and /physical/experience/docs",
                        "metadata", Map.of(
                                "baseDirectory", "/physical/experience/.opencode/skills/review",
                                "/physical/experience/commands/review", Map.of(
                                        "references", List.of("/physical/experience/docs")))))))));

        Object response = fixture.service.withUser(
                userId,
                () -> fixture.service.listCommands(
                        "wrk_exp_1234567890abcdef", "trace_1234567890abcdef"));

        assertThat(response.toString())
                .contains("<experience-workspace>")
                .doesNotContain("/physical/experience");
    }

    @Test
    void experienceWorkspaceRedactsPhysicalRootFromRuntimeErrors() {
        Fixture fixture = new Fixture();
        UserId userId = new UserId("usr_1234567890abcdef");
        fixture.useExperienceWorkspace();
        when(fixture.assignmentService.requireReadyProcess(
                        userId,
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(Fixture.userProcessNode(
                        "node_ocp_1234567890abcdef", "http://10.8.0.12:4096")));
        when(fixture.facade.runtime(any())).thenReturn(Mono.error(new PlatformException(
                ErrorCode.OPENCODE_BAD_GATEWAY,
                "runtime failed at /physical/experience",
                Map.of("directory", "/physical/experience/.opencode"))));

        assertThatThrownBy(() -> fixture.service.withUser(
                        userId,
                        () -> fixture.service.listAgents(
                                "wrk_exp_1234567890abcdef", "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.getMessage())
                            .contains("<experience-workspace>")
                            .doesNotContain("/physical/experience");
                    assertThat(exception.details().toString())
                            .contains("<experience-workspace>")
                            .doesNotContain("/physical/experience");
                });
    }

    @Test
    void experienceWorkspaceRejectsExperimentalWorktreeApisBeforeForwarding() {
        Fixture fixture = new Fixture();
        UserId userId = new UserId("usr_1234567890abcdef");
        fixture.useExperienceWorkspace();
        when(fixture.assignmentService.requireReadyProcess(
                        userId,
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(Fixture.userProcessNode(
                        "node_ocp_1234567890abcdef", "http://10.8.0.12:4096")));

        assertExperienceRuntimeFileApiForbidden(() -> fixture.service.withUser(
                userId,
                () -> fixture.service.listWorktrees(
                        "wrk_exp_1234567890abcdef", "trace_1234567890abcdef")));
        assertExperienceRuntimeFileApiForbidden(() -> fixture.service.withUser(
                userId,
                () -> fixture.service.createWorktree(
                        Map.of("workspaceId", "wrk_exp_1234567890abcdef", "name", "feature"),
                        "trace_1234567890abcdef")));
        assertExperienceRuntimeFileApiForbidden(() -> fixture.service.withUser(
                userId,
                () -> fixture.service.removeWorktree(
                        Map.of("workspaceId", "wrk_exp_1234567890abcdef", "name", "feature"),
                        "trace_1234567890abcdef")));
        assertExperienceRuntimeFileApiForbidden(() -> fixture.service.withUser(
                userId,
                () -> fixture.service.resetWorktree(
                        Map.of("workspaceId", "wrk_exp_1234567890abcdef"),
                        "trace_1234567890abcdef")));

        verify(fixture.facade, never()).runtime(any());
    }

    @Test
    void staticTokenCannotRouteHistoricalExperienceWorkspace() {
        Fixture fixture = new Fixture();
        fixture.useExperienceWorkspace();

        assertExperienceRuntimeFileApiForbidden(() -> fixture.service.listAgents(
                "wrk_exp_1234567890abcdef", "trace_1234567890abcdef"));

        verify(fixture.facade, never()).runtime(any());
        verify(fixture.workspaceAccessAuthorizer, never()).requireAccess(any(), any());
    }

    @Test
    void worktreeMutationRejectsMissingWorkspaceAndUnlistedPhysicalDirectory() {
        Fixture fixture = new Fixture();
        UserId userId = new UserId("usr_1234567890abcdef");
        when(fixture.assignmentService.requireReadyProcess(
                        userId,
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(Fixture.userProcessNode(
                        "node_ocp_1234567890abcdef", "http://10.8.0.12:4096")));
        when(fixture.facade.runtime(any())).thenAnswer(invocation -> {
            OpencodeRuntimeCommand command = invocation.getArgument(0);
            Object data = "/api/location".equals(command.path())
                    ? Map.of("project", Map.of("id", "prj_demo"))
                    : List.of(Map.of("name", "feature", "directory", "/tmp/demo/.worktrees/feature"));
            return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(data)));
        });

        assertThatThrownBy(() -> fixture.service.withUser(
                        userId,
                        () -> fixture.service.removeWorktree(
                                Map.of("directory", "/physical/experience"),
                                "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> fixture.service.withUser(
                        userId,
                        () -> fixture.service.removeWorktree(
                                Map.of(
                                        "workspaceId", "wrk_1234567890abcdef",
                                        "directory", "/physical/experience"),
                                "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/experimental/worktree");
    }

    private void assertExperienceRuntimeFileApiForbidden(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void workspaceRuntimeUsesAssignedUserProcessWhenUserContextExists() {
        Fixture fixture = new Fixture();
        ExecutionNode userNode = Fixture.userProcessNode("node_ocp_1234567890abcdef", "http://10.8.0.12:4096");
        when(fixture.assignmentService.requireReadyProcess(
                        new UserId("usr_1234567890abcdef"),
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(userNode));
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("healthy", true)))));

        fixture.service.withUser(
                new UserId("usr_1234567890abcdef"),
                () -> fixture.service.runtimeStatus("wrk_1234567890abcdef", "trace_1234567890abcdef"));

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.node().baseUrl()).isEqualTo("http://10.8.0.12:4096");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        verify(fixture.executionNodeRepository, never()).findRoutableNodes(1);
    }

    @Test
    void workspaceRuntimeKeepsFixedNodeFallbackWithoutUserContext() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("healthy", true)))));

        fixture.service.runtimeStatus("wrk_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.node().baseUrl()).isEqualTo("http://127.0.0.1:4096");
        verify(fixture.assignmentService, never()).requireReadyProcess(any(), anyString(), anyString());
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
    void sideQuestionForksSendsOneMessageAndDeletesTemporarySessionWithoutCompactingSmallContext() {
        Fixture fixture = new Fixture();
        when(fixture.facade.sessionMessages(any())).thenReturn(Mono.just(new com.enterprise.testagent.opencode.client.OpencodeSessionMessagesResult(
                List.of(new com.enterprise.testagent.opencode.client.OpencodeSessionMessage(
                        Map.of("role", "user"),
                        List.of(Map.of("type", "text", "text", "short context")))),
                null,
                null)));
        when(fixture.facade.runtime(any())).thenAnswer(invocation -> {
            OpencodeRuntimeCommand command = invocation.getArgument(0);
            return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(switch (command.method() + " " + command.path()) {
                case "POST /session/ses_remote1234567890abcdef/fork" -> Map.of("id", "ses_side1234567890abcdef");
                case "GET /session/ses_side1234567890abcdef/message" -> Map.of("data", List.of(Map.of(
                        "type", "assistant", "content", List.of(Map.of("type", "text", "text", "answer from context")))));
                case "DELETE /session/ses_side1234567890abcdef" -> Map.of("deleted", true);
                default -> Map.of("accepted", true);
            })));
        });

        var result = fixture.service.sideQuestion(
                "ses_1234567890abcdef",
                new SideQuestionInput("what did we decide?", "last_message", "plan", "anthropic/claude-sonnet"),
                "trace_1234567890abcdef");

        assertThat(result.answer()).isEqualTo("answer from context");
        assertThat(result.compacted()).isFalse();
        verify(fixture.facade).runtime(org.mockito.ArgumentMatchers.argThat(command ->
                command != null
                        && command.method().equals("POST")
                        && command.path().endsWith("/prompt")
                        && String.valueOf(((Map<?, ?>) command.body()).get("text"))
                                .startsWith(SideQuestionPolicy.SYSTEM_PROMPT)));
        verify(fixture.facade).runtime(org.mockito.ArgumentMatchers.argThat(command ->
                command != null && command.method().equals("DELETE") && command.path().endsWith("ses_side1234567890abcdef")));
        verify(fixture.facade, never()).runtime(org.mockito.ArgumentMatchers.argThat(command -> command != null && command.path().endsWith("/summarize")));
    }

    @Test
    void sideQuestionCompactsTemporaryForkWhenContextExceedsBudget() {
        Fixture fixture = new Fixture();
        String largeText = "x".repeat(50_000);
        when(fixture.facade.sessionMessages(any())).thenReturn(Mono.just(new com.enterprise.testagent.opencode.client.OpencodeSessionMessagesResult(
                List.of(new com.enterprise.testagent.opencode.client.OpencodeSessionMessage(
                        Map.of("role", "assistant"),
                        List.of(Map.of("type", "text", "text", largeText)))),
                null,
                null)));
        when(fixture.facade.runtime(any())).thenAnswer(invocation -> {
            OpencodeRuntimeCommand command = invocation.getArgument(0);
            return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(switch (command.method() + " " + command.path()) {
                case "POST /session/ses_remote1234567890abcdef/fork" -> Map.of("id", "ses_side1234567890abcdef");
                case "POST /session/ses_side1234567890abcdef/summarize" -> Map.of("ok", true);
                case "GET /session/ses_side1234567890abcdef/message" -> Map.of("data", List.of(Map.of(
                        "type", "assistant", "content", List.of(Map.of("type", "text", "text", "compacted answer")))));
                case "DELETE /session/ses_side1234567890abcdef" -> Map.of("deleted", true);
                default -> Map.of("accepted", true);
            })));
        });

        var result = fixture.service.sideQuestion(
                "ses_1234567890abcdef",
                new SideQuestionInput("summarize the current direction", null, "plan", "anthropic/claude-sonnet"),
                "trace_1234567890abcdef");

        assertThat(result.answer()).isEqualTo("compacted answer");
        assertThat(result.compacted()).isTrue();
        verify(fixture.facade).runtime(org.mockito.ArgumentMatchers.argThat(command ->
                command != null && command.method().equals("POST") && command.path().endsWith("/summarize")));
    }

    @Test
    void sideQuestionRejectsPseudoToolCallTextInsteadOfShowingItAsAnswer() {
        Fixture fixture = new Fixture();
        when(fixture.facade.sessionMessages(any())).thenReturn(Mono.just(new com.enterprise.testagent.opencode.client.OpencodeSessionMessagesResult(
                List.of(new com.enterprise.testagent.opencode.client.OpencodeSessionMessage(
                        Map.of("role", "user"),
                        List.of(Map.of("type", "text", "text", "short context")))),
                null,
                null)));
        when(fixture.facade.runtime(any())).thenAnswer(invocation -> {
            OpencodeRuntimeCommand command = invocation.getArgument(0);
            return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(switch (command.method() + " " + command.path()) {
                case "POST /session/ses_remote1234567890abcdef/fork" -> Map.of("id", "ses_side1234567890abcdef");
                case "GET /session/ses_side1234567890abcdef/message" -> Map.of("data", List.of(Map.of(
                        "type", "assistant", "content", List.of(Map.of("type", "text", "text", "<tool_calls:abc>\\n<tool_call:abc>Bash\\ncommand=ls\\n</tool_calls:abc>")))));
                case "DELETE /session/ses_side1234567890abcdef" -> Map.of("deleted", true);
                default -> Map.of("accepted", true);
            })));
        });

        assertThatThrownBy(() -> fixture.service.sideQuestion(
                        "ses_1234567890abcdef",
                        new SideQuestionInput("what did we decide?", null, "plan", "anthropic/claude-sonnet"),
                        "trace_1234567890abcdef"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("natural-language");
    }

    @Test
    void sideQuestionKeepsFinalAnswerAfterPseudoToolCallBlock() {
        Fixture fixture = new Fixture();
        when(fixture.facade.sessionMessages(any())).thenReturn(Mono.just(new com.enterprise.testagent.opencode.client.OpencodeSessionMessagesResult(
                List.of(new com.enterprise.testagent.opencode.client.OpencodeSessionMessage(
                        Map.of("role", "user"),
                        List.of(Map.of("type", "text", "text", "short context")))),
                null,
                null)));
        when(fixture.facade.runtime(any())).thenAnswer(invocation -> {
            OpencodeRuntimeCommand command = invocation.getArgument(0);
            return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(switch (command.method() + " " + command.path()) {
                case "POST /session/ses_remote1234567890abcdef/fork" -> Map.of("id", "ses_side1234567890abcdef");
                case "GET /session/ses_side1234567890abcdef/message" -> Map.of("data", List.of(Map.of(
                        "type", "assistant", "content", List.of(Map.of("type", "text", "text", "<tool_calls:abc>\n<tool_call:abc>Bash\ncommand=ls\n</tool_calls:abc>\n实际答案")))));
                case "DELETE /session/ses_side1234567890abcdef" -> Map.of("deleted", true);
                default -> Map.of("accepted", true);
            })));
        });

        assertThat(fixture.service.sideQuestion(
                        "ses_1234567890abcdef",
                        new SideQuestionInput("what did we decide?", null, "plan", "anthropic/claude-sonnet"),
                        "trace_1234567890abcdef"))
                .extracting(SideQuestionResult::answer)
                .isEqualTo("实际答案");
    }

    @Test
    void getEffectiveConfigUsesInstanceConfigPath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("enabled_providers", List.of("enterprise-qwen"))))));

        fixture.service.getEffectiveConfig("wrk_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/config");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
    }

    @Test
    void getConfigKeepsGlobalConfigCompatibilityPath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("theme", "dark")))));

        fixture.service.getConfig("wrk_1234567890abcdef", "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("GET");
        assertThat(command.path()).isEqualTo("/api/config");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
    }

    @Test
    void authorizeProviderOAuthUsesProviderOAuthPathAndBody() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenAnswer(invocation -> {
            OpencodeRuntimeCommand command = invocation.getArgument(0);
            Object data = "GET".equals(command.method())
                    ? Map.of("data", Map.of("methods", List.of(Map.of("id", "oauth_default", "type", "oauth"))))
                    : Map.of("url", "https://auth.example");
            return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(data)));
        });

        fixture.service.authorizeProviderOAuth("anthropic", Map.of("callbackUrl", "http://localhost/callback"), "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/provider/anthropic/oauth/authorize");
        assertThat(command.body()).isEqualTo(Map.of("methodID", "oauth_default"));
    }

    @Test
    void createWorktreeUsesExperimentalWorktreePath() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenAnswer(invocation -> {
            OpencodeRuntimeCommand command = invocation.getArgument(0);
            Object data = "/api/location".equals(command.path())
                    ? Map.of("project", Map.of("id", "prj_demo"))
                    : Map.of("directory", "/tmp/demo/.worktrees/feature");
            return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(data)));
        });

        fixture.service.createWorktree(Map.of("workspaceId", "wrk_1234567890abcdef", "branch", "feature"), "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/experimental/worktree");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        assertThat(command.body()).isEqualTo(Map.of("projectID", "prj_demo", "branch", "feature"));
    }

    @Test
    void shareSessionUsesPlatformCollaborationShareContract() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.service.shareSession(
                        "ses_1234567890abcdef", "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.API_GONE);
                    assertThat(exception.getMessage()).contains("collaboration-share");
                });
        verify(fixture.facade, never()).runtime(any());
    }

    @Test
    void unshareSessionUsesPlatformCollaborationShareContract() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.service.unshareSession(
                        "ses_1234567890abcdef", "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.API_GONE);
                    assertThat(exception.getMessage()).contains("collaboration-share");
                });
        verify(fixture.facade, never()).runtime(any());
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
    void listQuestionsFiltersProcessWidePendingRequestsByBoundRemoteSession() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(List.of(
                        Map.of("id", "question_current", "sessionID", "ses_remote1234567890abcdef"),
                        Map.of("id", "question_other", "sessionID", "ses_other1234567890abcdef"))))));

        Object result = fixture.service.listQuestions("ses_1234567890abcdef", "trace_1234567890abcdef");

        assertThat(result).isEqualTo(List.of(Map.of(
                "id", "question_current",
                "sessionID", "ses_remote1234567890abcdef")));
        assertThat(fixture.captureCommand().path()).isEqualTo("/question");
    }

    @Test
    void listQuestionsProjectsV2FormFieldsToPlatformQuestions() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("data", List.of(Map.of(
                        "id", "frm_current",
                        "sessionID", "ses_remote1234567890abcdef",
                        "title", "选择部署环境",
                        "fields", List.of(Map.of(
                                "key", "environment",
                                "type", "string",
                                "title", "部署到哪里",
                                "options", List.of(Map.of("value", "staging", "label", "测试环境")))))))))));

        Object result = fixture.service.listQuestions("ses_1234567890abcdef", "trace_1234567890abcdef");

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> envelope = (Map<?, ?>) result;
        Map<?, ?> question = (Map<?, ?>) ((List<?>) envelope.get("data")).getFirst();
        assertThat(question.get("requestId")).isEqualTo("frm_current");
        Map<?, ?> item = (Map<?, ?>) ((List<?>) question.get("questions")).getFirst();
        assertThat(item.get("questionId")).isEqualTo("environment");
        assertThat(item.get("kind")).isEqualTo("single");
        assertThat(item.get("text")).isEqualTo("部署到哪里");
    }

    @Test
    void replyQuestionUsesV2FormFieldKeys() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenAnswer(invocation -> {
            OpencodeRuntimeCommand command = invocation.getArgument(0);
            if ("GET".equals(command.method())) {
                return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(Map.of("data", Map.of(
                        "id", "frm_current",
                        "fields", List.of(Map.of("key", "environment", "type", "string")))))));
            }
            return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(Map.of("accepted", true))));
        });

        fixture.service.replyQuestion("ses_1234567890abcdef", "frm_current",
                Map.of("answers", List.of("staging")), "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.body()).isEqualTo(Map.of("answer", Map.of("environment", "staging")));
    }

    @Test
    void listPermissionsFiltersEnvelopeItemsByBoundRemoteSession() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("data", List.of(
                        Map.of("id", "permission_current", "sessionID", "ses_remote1234567890abcdef"),
                        Map.of("id", "permission_other", "sessionID", "ses_other1234567890abcdef")))))));

        Object result = fixture.service.listPermissions("ses_1234567890abcdef", "trace_1234567890abcdef");

        assertThat(result).isEqualTo(Map.of("data", List.of(Map.of(
                "id", "permission_current",
                "sessionID", "ses_remote1234567890abcdef"))));
        assertThat(fixture.captureCommand().path()).isEqualTo("/permission");
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
        assertThat(command.path()).isEqualTo("/session/ses_remote1234567890abcdef/permission/req_1/reply");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        assertThat(command.body()).isEqualTo(Map.of("decision", "once"));
    }

    @Test
    void replyQuestionNormalizesFlatAnswersToNestedShape() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("accepted", true)))));

        // V2 Form.Reply 使用按问题 key 编排的 answer map；旧扁平数组使用稳定的数字 key 兼容。
        fixture.service.replyQuestion(
                "ses_1234567890abcdef",
                "req_1",
                Map.of("answers", List.of("confirm")),
                "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/session/ses_remote1234567890abcdef/form/req_1/reply");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        assertThat(command.body()).isEqualTo(Map.of("answer", Map.of("0", "confirm")));
        verify(fixture.runApplicationService).recordQuestionReplyAcknowledged(
                new SessionId("ses_1234567890abcdef"),
                "ses_remote1234567890abcdef",
                "req_1",
                List.of(List.of("confirm")),
                "trace_1234567890abcdef");
    }

    @Test
    void replyQuestionWrapsMultipleAnswersIntoSingleInnerList() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("accepted", true)))));

        // 多选旧数组保留每个位置，避免丢失 V2 表单返回所需的答案值。
        fixture.service.replyQuestion(
                "ses_1234567890abcdef",
                "req_1",
                Map.of("answers", List.of("a", "b")),
                "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.body()).isEqualTo(Map.of("answer", Map.of("0", "a", "1", "b")));
    }

    @Test
    void replyQuestionPassesThroughNestedAnswersForMultipleSubQuestions() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("accepted", true)))));

        // 多个子问题使用数字 key 映射到各自的答案数组。
        fixture.service.replyQuestion(
                "ses_1234567890abcdef",
                "req_1",
                Map.of("answers", List.of(List.of("沙箱"), List.of("两个"))),
                "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.body()).isEqualTo(Map.of(
                "answer", Map.of("0", List.of("沙箱"), "1", List.of("两个"))));
    }

    @Test
    void replyQuestionMapsRemoteMissingRequestToRecoverableConflict() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.error(new PlatformException(
                ErrorCode.OPENCODE_BAD_GATEWAY,
                "TestAgent 服务响应异常",
                Map.of("status", 404))));

        assertThatThrownBy(() -> fixture.service.replyQuestion(
                        "ses_1234567890abcdef",
                        "que_expired1234567890",
                        Map.of("answers", List.of("A")),
                        "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).contains("请求已失效");
                    assertThat(exception.details()).containsEntry("reason", "REMOTE_INTERACTION_EXPIRED");
                });
    }

    private static final class Fixture {
        private final WorkspaceRepository workspaceRepository = org.mockito.Mockito.mock(WorkspaceRepository.class);
        private final SessionRepository sessionRepository = org.mockito.Mockito.mock(SessionRepository.class);
        private final ExecutionNodeRepository executionNodeRepository = org.mockito.Mockito.mock(ExecutionNodeRepository.class);
        private final AgentSessionBindingRepository bindingRepository = new FakeAgentSessionBindingRepository();
        private final OpencodeClientFacade facade = org.mockito.Mockito.mock(OpencodeClientFacade.class);
        private final AgentRuntimeRegistry runtimeRegistry =
                new AgentRuntimeRegistry(List.of(new OpencodeAgentRuntime(facade)));
        private final UserOpencodeProcessAssignmentService assignmentService =
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        private final ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer =
                org.mockito.Mockito.mock(ConversationWorkspaceAccessAuthorizer.class);
        private final RunApplicationService runApplicationService =
                org.mockito.Mockito.mock(RunApplicationService.class);
        private final SessionApplicationService sessionApplicationService =
                org.mockito.Mockito.mock(SessionApplicationService.class);
        private final OpencodeRuntimeApplicationService service;

        private Fixture() {
            when(workspaceRepository.findById(new WorkspaceId("wrk_1234567890abcdef"))).thenReturn(Optional.of(workspace()));
            when(sessionRepository.findById(new SessionId("ses_1234567890abcdef"))).thenReturn(Optional.of(session()));
            when(sessionRepository.attachOpencodeSession(any(), anyString(), any(), any(), anyString()))
                    .thenAnswer(invocation -> Optional.of(session().attachOpencodeSession(
                            invocation.getArgument(1),
                            invocation.getArgument(2),
                            invocation.getArgument(3),
                            invocation.getArgument(4))));
            when(executionNodeRepository.findRoutableNodes(1)).thenReturn(List.of(node()));
            when(executionNodeRepository.findById(new ExecutionNodeId("node_1234567890abcdef"))).thenReturn(Optional.of(node()));
            when(facade.sessionExists(any())).thenReturn(Mono.just(true));
            service = new OpencodeRuntimeApplicationService(
                    runtimeRegistry,
                    new AgentRuntimeTargetResolver(
                            workspaceRepository,
                            sessionRepository,
                            executionNodeRepository,
                            runtimeRegistry,
                            bindingRepository,
                            assignmentService,
                            ManagedWorkspacePathResolver.legacyOnly(),
                            workspaceAccessAuthorizer),
                    new ObjectMapper(),
                    null,
                    runApplicationService);
            service.configureSessionApplicationService(sessionApplicationService);
        }

        private void useExperienceWorkspace() {
            when(workspaceRepository.findById(new WorkspaceId("wrk_exp_1234567890abcdef")))
                    .thenReturn(Optional.of(new Workspace(
                            new WorkspaceId("wrk_exp_1234567890abcdef"),
                            "体验工作区",
                            "/physical/experience",
                            WorkspaceStatus.ACTIVE,
                            NOW,
                            NOW,
                            "server-1",
                            "trace_1234567890abcdef")));
        }

        private void useExperienceSession(UserId owner) {
            useExperienceWorkspace();
            when(sessionRepository.findById(new SessionId("ses_1234567890abcdef")))
                    .thenReturn(Optional.of(new Session(
                            new SessionId("ses_1234567890abcdef"),
                            new WorkspaceId("wrk_exp_1234567890abcdef"),
                            "体验会话",
                            SessionStatus.ACTIVE,
                            NOW,
                            NOW,
                            "trace_1234567890abcdef",
                            "ses_remote1234567890abcdef",
                            new ExecutionNodeId("node_1234567890abcdef"))
                            .withSource(
                                    com.enterprise.testagent.domain.session.ConversationSourceType.MANUAL,
                                    null,
                                    owner)));
        }

        private OpencodeRuntimeCommand captureCommand() {
            ArgumentCaptor<OpencodeRuntimeCommand> captor = ArgumentCaptor.forClass(OpencodeRuntimeCommand.class);
            verify(facade, atLeastOnce()).runtime(captor.capture());
            return captor.getAllValues().getLast();
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
    }

    private static ExecutionNode localClientNode() {
        return new ExecutionNode(
                new ExecutionNodeId("node_localprotected1234567890"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                NOW,
                Set.of("chat", "protected-agent-execution"),
                NOW,
                NOW,
                "trace_protected_catalog",
                RuntimeKind.LOCAL_CLIENT,
                "lci_protectedcatalog1234567890",
                7L);
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
}
