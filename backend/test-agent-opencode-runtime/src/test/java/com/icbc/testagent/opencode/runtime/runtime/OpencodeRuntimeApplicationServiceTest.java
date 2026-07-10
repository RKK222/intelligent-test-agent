package com.icbc.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.OpencodeAgentRuntime;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.opencode.client.OpencodeClientFacade;
import com.icbc.testagent.opencode.client.OpencodeCreateSessionCommand;
import com.icbc.testagent.opencode.client.OpencodeCreateSessionResult;
import com.icbc.testagent.opencode.client.OpencodeRuntimeCommand;
import com.icbc.testagent.opencode.client.OpencodeRuntimeResult;
import com.icbc.testagent.opencode.client.OpencodeSessionExistsCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
        assertThat(command.path()).isEqualTo("/global/health");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        assertThat(result).isInstanceOf(Map.class);
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
    void sessionRuntimeReusesRemoteSessionWhenBindingMatchesCurrentUserProcess() {
        Fixture fixture = new Fixture();
        ExecutionNode userNode = Fixture.node();
        when(fixture.assignmentService.requireReadyProcess(
                        new UserId("usr_1234567890abcdef"),
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(userNode));
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("url", "https://opencode.ai/s/abc")))));

        fixture.service.withUser(
                new UserId("usr_1234567890abcdef"),
                () -> fixture.service.shareSession("ses_1234567890abcdef", "trace_1234567890abcdef"));

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.path()).isEqualTo("/session/ses_remote1234567890abcdef/share");
        assertThat(command.node().baseUrl()).isEqualTo("http://127.0.0.1:4096");
        verify(fixture.facade).sessionExists(any(OpencodeSessionExistsCommand.class));
        verify(fixture.facade, never()).createSession(any());
    }

    @Test
    void sessionRuntimeRebuildsRemoteSessionWhenBindingNodeDiffersFromCurrentUserProcess() {
        Fixture fixture = new Fixture();
        ExecutionNode userNode = Fixture.userProcessNode("node_ocp_1234567890abcdef", "http://10.8.0.12:4096");
        when(fixture.assignmentService.requireReadyProcess(
                        new UserId("usr_1234567890abcdef"),
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(userNode));
        when(fixture.facade.createSession(any())).thenReturn(Mono.just(new OpencodeCreateSessionResult("ses_userremote1234567890abcdef")));
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("url", "https://opencode.ai/s/user")))));

        fixture.service.withUser(
                new UserId("usr_1234567890abcdef"),
                () -> fixture.service.shareSession("ses_1234567890abcdef", "trace_1234567890abcdef"));

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.path()).isEqualTo("/session/ses_userremote1234567890abcdef/share");
        assertThat(command.node().baseUrl()).isEqualTo("http://10.8.0.12:4096");
        AgentSessionBinding binding = fixture.bindingRepository
                .findBySessionIdAndAgentId(new SessionId("ses_1234567890abcdef"), "opencode")
                .orElseThrow();
        assertThat(binding.remoteSessionId()).isEqualTo("ses_userremote1234567890abcdef");
        assertThat(binding.executionNodeId()).isEqualTo(userNode.executionNodeId());
        verify(fixture.facade).createSession(any(OpencodeCreateSessionCommand.class));
    }

    @Test
    void sessionRuntimeRebuildsRemoteSessionWhenExistingBindingIsMissingRemotely() {
        Fixture fixture = new Fixture();
        ExecutionNode userNode = Fixture.node();
        when(fixture.assignmentService.requireReadyProcess(
                        new UserId("usr_1234567890abcdef"),
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(userNode));
        when(fixture.facade.sessionExists(any())).thenReturn(Mono.just(false));
        when(fixture.facade.createSession(any())).thenReturn(Mono.just(new OpencodeCreateSessionResult("ses_rebuilt1234567890abcdef")));
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("url", "https://opencode.ai/s/rebuilt")))));

        fixture.service.withUser(
                new UserId("usr_1234567890abcdef"),
                () -> fixture.service.shareSession("ses_1234567890abcdef", "trace_1234567890abcdef"));

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.path()).isEqualTo("/session/ses_rebuilt1234567890abcdef/share");
        AgentSessionBinding binding = fixture.bindingRepository
                .findBySessionIdAndAgentId(new SessionId("ses_1234567890abcdef"), "opencode")
                .orElseThrow();
        assertThat(binding.remoteSessionId()).isEqualTo("ses_rebuilt1234567890abcdef");
        verify(fixture.facade).createSession(any(OpencodeCreateSessionCommand.class));
    }

    @Test
    void sessionRuntimePropagatesRemoteSessionValidationFailures() {
        Fixture fixture = new Fixture();
        ExecutionNode userNode = Fixture.node();
        when(fixture.assignmentService.requireReadyProcess(
                        new UserId("usr_1234567890abcdef"),
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(userNode));
        when(fixture.facade.sessionExists(any())).thenReturn(Mono.error(new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "opencode 服务不可用")));

        assertThatThrownBy(() -> fixture.service.withUser(
                        new UserId("usr_1234567890abcdef"),
                        () -> fixture.service.shareSession("ses_1234567890abcdef", "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        verify(fixture.facade, never()).createSession(any());
        verify(fixture.facade, never()).runtime(any());
    }

    @Test
    void userRuntimeReturnsUnavailableWhenUserProcessIsNotReady() {
        Fixture fixture = new Fixture();
        when(fixture.assignmentService.requireReadyProcess(
                        new UserId("usr_1234567890abcdef"),
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenThrow(new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "请先初始化 opencode 进程"));

        assertThatThrownBy(() -> fixture.service.withUser(
                        new UserId("usr_1234567890abcdef"),
                        () -> fixture.service.listAgents("wrk_1234567890abcdef", "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));
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

    @Test
    void replyQuestionNormalizesFlatAnswersToNestedShape() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("accepted", true)))));

        // 单选：前端发送扁平 [label]，应归一化为 [[label]]。
        fixture.service.replyQuestion(
                "ses_1234567890abcdef",
                "req_1",
                Map.of("answers", List.of("confirm")),
                "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/question/req_1/reply");
        assertThat(command.directory()).isEqualTo("/tmp/demo");
        assertThat(command.body()).isEqualTo(Map.of("answers", List.of(List.of("confirm"))));
    }

    @Test
    void replyQuestionWrapsMultipleAnswersIntoSingleInnerList() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("accepted", true)))));

        // 多选：前端发送扁平 [l1, l2]（同一问题的多个 label），应整体包成 [[l1, l2]]。
        fixture.service.replyQuestion(
                "ses_1234567890abcdef",
                "req_1",
                Map.of("answers", List.of("a", "b")),
                "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.body()).isEqualTo(Map.of("answers", List.of(List.of("a", "b"))));
    }

    @Test
    void replyQuestionPassesThroughNestedAnswersForMultipleSubQuestions() {
        Fixture fixture = new Fixture();
        when(fixture.facade.runtime(any())).thenReturn(Mono.just(new OpencodeRuntimeResult(
                objectMapper.valueToTree(Map.of("accepted", true)))));

        // 前端对同一请求下的多个子问题一次性提交嵌套 [[q1], [q2]]，应原样透传不重复包装。
        fixture.service.replyQuestion(
                "ses_1234567890abcdef",
                "req_1",
                Map.of("answers", List.of(List.of("沙箱"), List.of("两个"))),
                "trace_1234567890abcdef");

        OpencodeRuntimeCommand command = fixture.captureCommand();
        assertThat(command.body()).isEqualTo(Map.of("answers", List.of(List.of("沙箱"), List.of("两个"))));
    }

    @Test
    void nativeTitleAgentUsesTemporarySessionAndAlwaysDeletesIt() {
        Fixture fixture = new Fixture();
        ExecutionNode userNode = Fixture.userProcessNode("node_ocp_1234567890abcdef", "http://10.8.0.12:4096");
        when(fixture.assignmentService.requireReadyProcess(
                        new UserId("usr_1234567890abcdef"),
                        "opencode",
                        "trace_1234567890abcdef"))
                .thenReturn(new UserOpencodeProcessAssignment(userNode));
        List<OpencodeRuntimeCommand> commands = new ArrayList<>();
        when(fixture.facade.runtime(any())).thenAnswer(invocation -> {
            OpencodeRuntimeCommand command = invocation.getArgument(0);
            commands.add(command);
            Object response = switch (command.path()) {
                case "/session" -> Map.of("id", "ses_title1234567890abcdef");
                case "/session/ses_title1234567890abcdef/prompt_async" -> Map.of("accepted", true);
                case "/session/ses_title1234567890abcdef/message" -> List.of(Map.of(
                        "info", Map.of("role", "assistant", "agent", "title"),
                        "parts", List.of(Map.of("type", "text", "text", "金额新增参数审核测试"))));
                case "/session/ses_title1234567890abcdef" -> Map.of("deleted", true);
                default -> throw new AssertionError("unexpected path: " + command.path());
            };
            return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(response)));
        });

        Optional<String> title = fixture.service.withUser(
                new UserId("usr_1234567890abcdef"),
                () -> fixture.service.generateNativeSessionTitle(
                        "wrk_1234567890abcdef",
                        "测试金额新增参数审核",
                        Duration.ofSeconds(1),
                        Duration.ofMillis(1),
                        "trace_1234567890abcdef"));

        assertThat(title).contains("金额新增参数审核测试");
        assertThat(commands).extracting(OpencodeRuntimeCommand::path).containsExactly(
                "/session",
                "/session/ses_title1234567890abcdef/prompt_async",
                "/session/ses_title1234567890abcdef/message",
                "/session/ses_title1234567890abcdef");
        assertThat(commands.get(1).body()).isEqualTo(Map.of(
                "agent", "title",
                "parts", List.of(Map.of("type", "text", "text", "测试金额新增参数审核"))));
        assertThat(commands.get(3).method()).isEqualTo("DELETE");
    }

    private static final class Fixture {
        private final WorkspaceRepository workspaceRepository = org.mockito.Mockito.mock(WorkspaceRepository.class);
        private final SessionRepository sessionRepository = org.mockito.Mockito.mock(SessionRepository.class);
        private final ExecutionNodeRepository executionNodeRepository = org.mockito.Mockito.mock(ExecutionNodeRepository.class);
        private final AgentSessionBindingRepository bindingRepository = new FakeAgentSessionBindingRepository();
        private final OpencodeClientFacade facade = org.mockito.Mockito.mock(OpencodeClientFacade.class);
        private final UserOpencodeProcessAssignmentService assignmentService =
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        private final OpencodeRuntimeApplicationService service = new OpencodeRuntimeApplicationService(
                workspaceRepository,
                sessionRepository,
                executionNodeRepository,
                new AgentRuntimeRegistry(List.of(new OpencodeAgentRuntime(facade))),
                bindingRepository,
                new ObjectMapper(),
                assignmentService);

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
