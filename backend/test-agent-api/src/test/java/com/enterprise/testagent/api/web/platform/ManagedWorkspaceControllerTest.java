package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.workspace.ManagedWorkspaceApplicationService;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.BranchPreferenceResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.ManagedApplicationResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.PersonalWorkspacePublishPreviewResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.WorkspaceRuntimeResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.WorkspaceGitConflictResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ManagedWorkspaceControllerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void authenticatedUserCanListMemberApplications() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.listApplications(USER_ID))
                .thenReturn(List.of(new ManagedApplicationResponse("app_gcms", "F-GCMS", true)));

        client(service).get()
                .uri("/api/internal/platform/workspace-management/applications")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].appId").isEqualTo("app_gcms")
                .jsonPath("$.data[0].appName").isEqualTo("F-GCMS");
    }

    @Test
    void createVersionPassesCurrentUserAndTraceId() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        WorkspaceRuntimeResponse runtime = new WorkspaceRuntimeResponse(
                "wks_123",
                "F-GCMS-20260707",
                "/data/appworkspace/20260707/repo/F-GCMS/workspace",
                "ACTIVE",
                "10.8.0.12",
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-23T00:00:00Z"),
                "app_gcms",
                null,
                null);
        UserOpencodeProcessAssignmentService assignmentService = readyAssignmentService("10.8.0.12");
        when(service.createVersion(eq("app_gcms"), eq("aws_123"), eq("20260707"), eq("feature_testagent_20260707"), eq(USER_ID), eq("10.8.0.12"), eq(TRACE_ID)))
                .thenReturn(new ApplicationWorkspaceVersionResponse(
                        "awv_123",
                        "aws_123",
                        "app_gcms",
                        "repo_123",
                        "20260707",
                        "feature_testagent_20260707",
                        "/data/appworkspace/20260707/repo",
                        "/data/appworkspace/20260707/repo/F-GCMS/workspace",
                        runtime,
                        "ACTIVE",
                        Instant.parse("2026-06-23T00:00:00Z"),
                        Instant.parse("2026-06-23T00:00:00Z")));

        client(service, assignmentService).post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/workspace-templates/aws_123/versions")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"version":"20260707","branch":"feature_testagent_20260707"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.versionId").isEqualTo("awv_123")
                .jsonPath("$.data.runtimeWorkspace.workspaceId").isEqualTo("wks_123");
    }

    @Test
    void gitPullVersionPassesAgentLinuxServerId() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = readyAssignmentService("10.8.0.12");
        WorkspaceRuntimeResponse runtime = runtimeWorkspace();
        when(service.gitPullVersion(eq("awv_123"), eq(USER_ID), eq("10.8.0.12"), eq(TRACE_ID)))
                .thenReturn(new ApplicationWorkspaceVersionResponse(
                        "awv_123",
                        "aws_123",
                        "app_gcms",
                        "repo_123",
                        "20260707",
                        "feature_testagent_20260707",
                        "/data/appworkspace/20260707/repo",
                        "/data/appworkspace/20260707/repo/F-GCMS/workspace",
                        runtime,
                        "ACTIVE",
                        Instant.parse("2026-06-23T00:00:00Z"),
                        Instant.parse("2026-06-23T00:00:00Z")));

        client(service, assignmentService).post()
                .uri("/api/internal/platform/workspace-management/workspace-versions/awv_123/git-pull")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.versionId").isEqualTo("awv_123");

        verify(service).gitPullVersion("awv_123", USER_ID, "10.8.0.12", TRACE_ID);
    }

    @Test
    void markRecentWorkspaceReturnsRuntimeWorkspace() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.markRecentWorkspace("wks_123", USER_ID))
                .thenReturn(new WorkspaceRuntimeResponse(
                        "wks_123",
                        "F-GCMS-20260707",
                        "/data/workspace",
                        "ACTIVE",
                        "127.0.0.1",
                        Instant.parse("2026-06-23T00:00:00Z"),
                        Instant.parse("2026-06-23T00:00:00Z"),
                        "app_gcms",
                        "awv_123",
                        "aws_123"));

        client(service).post()
                .uri("/api/internal/platform/workspace-management/workspaces/wks_123/recent")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workspaceId").isEqualTo("wks_123")
                .jsonPath("$.data.appId").isEqualTo("app_gcms")
                .jsonPath("$.data.versionId").isEqualTo("awv_123")
                .jsonPath("$.data.applicationWorkspaceId").isEqualTo("aws_123");

        verify(service).markRecentWorkspace("wks_123", USER_ID);
    }

    @Test
    void recentWorkspaceMayBeEmpty() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.recentWorkspace(USER_ID)).thenReturn(Optional.empty());

        client(service).get()
                .uri("/api/internal/platform/workspace-management/recent-workspace")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEmpty();
    }

    @Test
    void markRecentBranchForwardsBranchPayload() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.markRecentBranch(eq("app_gcms"), eq("wks_123"), eq("feature/personalized"), eq(USER_ID)))
                .thenReturn(new BranchPreferenceResponse(
                        "app_gcms",
                        "wks_123",
                        "feature/personalized",
                        Instant.parse("2026-06-24T00:00:00Z")));

        client(service).post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/workspaces/wks_123/branch-preference")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"branch":"feature/personalized"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.appId").isEqualTo("app_gcms")
                .jsonPath("$.data.workspaceId").isEqualTo("wks_123")
                .jsonPath("$.data.branch").isEqualTo("feature/personalized");

        verify(service).markRecentBranch("app_gcms", "wks_123", "feature/personalized", USER_ID);
    }

    @Test
    void recentBranchMayBeEmpty() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.recentBranch("app_gcms", "wks_123", USER_ID)).thenReturn(Optional.empty());

        client(service).get()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/workspaces/wks_123/branch-preference")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEmpty();
    }

    @Test
    void gitConflictEndpointsForwardPathResolutionAndUser() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.getWorkspaceGitConflict("wks_123", "src/Login.java", USER_ID))
                .thenReturn(new WorkspaceGitConflictResponse(
                        "src/Login.java", "UU", "base", "current", "incoming", "result"));

        client(service).get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/platform/workspace-management/workspaces/wks_123/git-conflict")
                        .queryParam("path", "src/Login.java")
                        .build())
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.currentContent").isEqualTo("current")
                .jsonPath("$.data.incomingContent").isEqualTo("incoming");

        client(service).post()
                .uri("/api/internal/platform/workspace-management/workspaces/wks_123/git-conflict/resolve")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"path":"src/Login.java","resolution":"MANUAL","content":"resolved"}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).resolveWorkspaceGitConflict(
                "wks_123", "src/Login.java", "MANUAL", "resolved", USER_ID);
    }

    @Test
    void publishPreviewAndResolveAllForwardNewGitContract() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.previewPersonalWorkspacePublish("psw_123", USER_ID, TRACE_ID))
                .thenReturn(new PersonalWorkspacePublishPreviewResponse(
                        "app-head", "personal-head", 2, 3, 1, 1, 1, 0, List.of("README.md")));

        client(service).post()
                .uri("/api/internal/platform/workspace-management/personal-workspaces/psw_123/publish-preview")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.applicationHead").isEqualTo("app-head")
                .jsonPath("$.data.incomingCommitCount").isEqualTo(2);

        client(service).post()
                .uri("/api/internal/platform/workspace-management/workspaces/wks_123/git-conflict/resolve-all")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"resolution\":\"CURRENT\"}")
                .exchange()
                .expectStatus().isOk();

        verify(service).resolveAllWorkspaceGitConflicts("wks_123", "CURRENT", USER_ID);
    }

    @Test
    void gitStageEndpointsForwardFilesAndUser() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);

        client(service).post()
                .uri("/api/internal/platform/workspace-management/workspaces/wks_123/git-stage")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"files":["src/Changed.java"]}
                        """)
                .exchange()
                .expectStatus().isOk();

        client(service).post()
                .uri("/api/internal/platform/workspace-management/workspaces/wks_123/git-unstage")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"files":["src/Changed.java"]}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).stageWorkspaceGitFiles("wks_123", List.of("src/Changed.java"), USER_ID);
        verify(service).unstageWorkspaceGitFiles("wks_123", List.of("src/Changed.java"), USER_ID);
    }

    @Test
    void superAdministratorPublishUsesTheSameDirectoryPolicy() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);

        client(service, readyAssignmentService("127.0.0.1"), List.of("SUPER_ADMIN")).post()
                .uri("/api/internal/platform/workspace-management/personal-workspaces/psw_123/publish")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"commitMessage":"spec: 超管发布设计","files":["spec/design.md"]}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).publishPersonalWorkspace(
                "psw_123",
                "spec: 超管发布设计",
                List.of("spec/design.md"),
                null,
                null,
                USER_ID,
                TRACE_ID);
    }

    @Test
    void ordinaryMemberCannotCommitApplicationAgentConfigThroughPersonalWorkspaceApi() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);

        client(service).post()
                .uri("/api/internal/platform/workspace-management/personal-workspaces/psw_123/commit")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"commitMessage":"agent: 修改应用技能","files":["src/App.java","./.opencode/skills/case-design/SKILL.md"]}
                        """)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.details.files[0]").isEqualTo("./.opencode/skills/case-design/SKILL.md");

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void ordinaryMemberCannotMutateApplicationAgentConfigThroughWorkspaceGitApi() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);

        client(service).post()
                .uri("/api/internal/platform/workspace-management/workspaces/wrk_personal/git-stage")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"files":[".opencode/agents/payment-test.md"]}
                        """)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void applicationAdministratorPublishesAgentConfigFromPersonalWorkspace() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);

        client(service, readyAssignmentService("127.0.0.1"), List.of("APP_ADMIN")).post()
                .uri("/api/internal/platform/workspace-management/personal-workspaces/psw_123/publish")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"commitMessage":"agent: 发布应用技能","files":[".opencode/skills/case-design/SKILL.md"]}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).publishPersonalWorkspace(
                "psw_123",
                "agent: 发布应用技能",
                List.of(".opencode/skills/case-design/SKILL.md"),
                null,
                null,
                USER_ID,
                TRACE_ID);
    }

    private WebTestClient client(ManagedWorkspaceApplicationService service) {
        return client(service, readyAssignmentService("127.0.0.1"));
    }

    private WebTestClient client(
            ManagedWorkspaceApplicationService service,
            UserOpencodeProcessAssignmentService assignmentService) {
        return client(service, assignmentService, List.of("USER"));
    }

    private WebTestClient client(
            ManagedWorkspaceApplicationService service,
            UserOpencodeProcessAssignmentService assignmentService,
            List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "888888888",
                "888888888",
                roles,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        return WebTestClient.bindToController(new ManagedWorkspaceController(service, assignmentService))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static UserOpencodeProcessAssignmentService readyAssignmentService(String linuxServerId) {
        UserOpencodeProcessAssignmentService assignmentService = org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        when(assignmentService.requireReadyProcess(eq(USER_ID), eq("opencode"), eq(TRACE_ID)))
                .thenReturn(new UserOpencodeProcessAssignment(
                        new ExecutionNode(
                                new ExecutionNodeId("node_1234567890abcdef"),
                                "http://" + linuxServerId + ":4096",
                                ExecutionNodeStatus.READY,
                                0,
                                4,
                                100,
                                Instant.parse("2026-06-23T00:00:00Z"),
                                Set.of("opencode"),
                                Instant.parse("2026-06-23T00:00:00Z"),
                                Instant.parse("2026-06-23T00:00:00Z"),
                                TRACE_ID),
                        linuxServerId));
        return assignmentService;
    }

    private static WorkspaceRuntimeResponse runtimeWorkspace() {
        return new WorkspaceRuntimeResponse(
                "wks_123",
                "F-GCMS-20260707",
                "/data/appworkspace/20260707/repo/F-GCMS/workspace",
                "ACTIVE",
                "10.8.0.12",
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-23T00:00:00Z"),
                "app_gcms",
                null,
                null);
    }
}
