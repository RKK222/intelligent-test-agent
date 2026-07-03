package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.workspace.ManagedWorkspaceApplicationService;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.BranchPreferenceResponse;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.ManagedApplicationResponse;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.WorkspaceRuntimeResponse;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.WorkspaceGitConflictResponse;
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

    private WebTestClient client(ManagedWorkspaceApplicationService service) {
        return client(service, readyAssignmentService("127.0.0.1"));
    }

    private WebTestClient client(
            ManagedWorkspaceApplicationService service,
            UserOpencodeProcessAssignmentService assignmentService) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "888888888",
                "888888888",
                List.of("USER"),
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
