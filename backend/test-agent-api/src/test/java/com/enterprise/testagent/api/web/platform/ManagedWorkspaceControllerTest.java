package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.workspace.ManagedWorkspaceApplicationService;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.ApplicationGitRefreshGroupResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.ApplicationGitRefreshResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.ApplicationGitRefreshScopeGroupResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.ApplicationGitRefreshScopeResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.ApplicationGitRefreshScopeWorkspaceResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.BranchPreferenceResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.GitRepositoryAccessResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.ManagedApplicationResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.PersonalWorkspacePublishPreviewResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.PersonalWorkspaceGitPullResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.WorkspaceRuntimeResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.WorkspaceGitCommitResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.WorkspaceGitConflictResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.WorkspaceGitMergeCompletionResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.AutomationActiveVersionResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.AutomationVersionServerSynchronizationResponse;
import com.enterprise.testagent.workspace.ManagedWorkspaceResponses.AutomationVersionSynchronizationResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    void automationVersionListOmitsPhysicalPathsAndRuntimeWorkspace() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.listVersions("aws_automation", USER_ID)).thenReturn(List.of(
                new ApplicationWorkspaceVersionResponse(
                        "awv_automation",
                        "aws_automation",
                        "app_gcms",
                        "repo_automation",
                        "20260819",
                        "main",
                        null,
                        null,
                        null,
                        "ACTIVE",
                        Instant.parse("2026-08-19T00:00:00Z"),
                        Instant.parse("2026-08-19T00:00:00Z"))));

        client(service).get()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/workspace-templates/aws_automation/versions")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].versionId").isEqualTo("awv_automation")
                .jsonPath("$.data[0].repoRootPath").doesNotExist()
                .jsonPath("$.data[0].workspaceRootPath").doesNotExist()
                .jsonPath("$.data[0].runtimeWorkspace").doesNotExist();
    }

    @Test
    void automationVersionActivationRequiresApplicationAdministratorAndPassesLogicalIds() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        AutomationActiveVersionResponse response = new AutomationActiveVersionResponse(
                "awv_2",
                "20260819",
                "main",
                "abc123",
                "READY",
                USER_ID.value(),
                Instant.parse("2026-08-19T00:00:00Z"));
        when(service.activateAutomationVersion("app_gcms", "awp_auto", "awv_2", USER_ID))
                .thenReturn(response);

        client(service).put()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/workspace-templates/awp_auto/active-version")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"versionId\":\"awv_2\"}")
                .exchange()
                .expectStatus().isForbidden();

        client(service, readyAssignmentService("127.0.0.1"), List.of("APP_ADMIN")).put()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/workspace-templates/awp_auto/active-version")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"versionId\":\"awv_2\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.versionId").isEqualTo("awv_2");

        verify(service).activateAutomationVersion("app_gcms", "awp_auto", "awv_2", USER_ID);
    }

    @Test
    void automationVersionSynchronizationRequiresAdministratorAndExposesOnlyLogicalServerProgress() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        AutomationVersionSynchronizationResponse response = new AutomationVersionSynchronizationResponse(
                "awp_auto",
                "接口自动化",
                "repo_auto",
                "自动化代码库",
                "awv_2",
                "20260819",
                "main",
                "abc123",
                "SYNCHRONIZING",
                "SYNCHRONIZE",
                2,
                1,
                List.of(
                        new AutomationVersionServerSynchronizationResponse(
                                "127.0.0.1", "local", "READY", true, "main", "abc123", true,
                                Instant.parse("2026-08-19T00:00:00Z"), null),
                        new AutomationVersionServerSynchronizationResponse(
                                "10.8.0.12", "remote", "PENDING", true, null, null, null, null, null)),
                TRACE_ID,
                null);
        when(service.synchronizeAutomationVersion(
                "app_gcms", "awp_auto", "awv_2", USER_ID, TRACE_ID)).thenReturn(response);
        when(service.automationVersionSynchronizationStatus(
                "app_gcms", "awp_auto", "awv_2", USER_ID, TRACE_ID)).thenReturn(response);

        client(service).post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/workspace-templates/awp_auto/versions/awv_2/synchronize")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden();

        WebTestClient administrator = client(service, readyAssignmentService("127.0.0.1"), List.of("APP_ADMIN"));
        administrator.post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/workspace-templates/awp_auto/versions/awv_2/synchronize")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.readyServerCount").isEqualTo(1)
                .jsonPath("$.data.targetServerCount").isEqualTo(2)
                .jsonPath("$.data.servers[1].serverName").isEqualTo("remote")
                .jsonPath("$.data.servers[1].repoRootPath").doesNotExist()
                .jsonPath("$.data.servers[1].workspaceRootPath").doesNotExist();

        administrator.get()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/workspace-templates/awp_auto/versions/awv_2/synchronization-status")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("SYNCHRONIZING");

        verify(service).synchronizeAutomationVersion(
                "app_gcms", "awp_auto", "awv_2", USER_ID, TRACE_ID);
        verify(service).automationVersionSynchronizationStatus(
                "app_gcms", "awp_auto", "awv_2", USER_ID, TRACE_ID);
    }

    @Test
    void personalWorkspaceCreationAndDefaultRepairRequireReadyProcess() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = org.mockito.Mockito.mock(
                UserOpencodeProcessAssignmentService.class);
        when(assignmentService.requireReadyProcess(USER_ID, "opencode", TRACE_ID))
                .thenThrow(new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "请先初始化 TestAgent 进程"));
        WebTestClient client = client(service, assignmentService);

        client.post()
                .uri("/api/internal/platform/workspace-management/workspace-versions/awv_123/personal-workspaces")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"workspaceName":"custom"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE.httpStatus())
                .expectBody()
                .jsonPath("$.code").isEqualTo("OPENCODE_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("请先初始化 TestAgent 进程");

        client.post()
                .uri("/api/internal/platform/workspace-management/workspace-versions/awv_123/ensure-default-personal-workspace")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE.httpStatus())
                .expectBody()
                .jsonPath("$.code").isEqualTo("OPENCODE_UNAVAILABLE");

        org.mockito.Mockito.verifyNoInteractions(service);
        org.mockito.Mockito.verify(assignmentService, org.mockito.Mockito.times(2))
                .requireReadyProcess(USER_ID, "opencode", TRACE_ID);
    }

    @Test
    void gitPullPersonalWorkspacePassesCurrentOwner() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.gitPullPersonalWorkspace(eq("pws_123"), eq(USER_ID), eq(TRACE_ID)))
                .thenReturn(new PersonalWorkspaceGitPullResponse(
                        "pws_123",
                        "awv_123",
                        "feature_testagent_20260707",
                        "commit_remote",
                        true,
                        false,
                        "NOT_REQUIRED",
                        null,
                        List.of("F-GCMS/workspace/docs/design.md")));

        client(service).post()
                .uri("/api/internal/platform/workspace-management/personal-workspaces/pws_123/git-pull")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.personalWorkspaceId").isEqualTo("pws_123")
                .jsonPath("$.data.updated").isEqualTo(true)
                .jsonPath("$.data.runtimeReloadStatus").isEqualTo("NOT_REQUIRED");

        verify(service).gitPullPersonalWorkspace("pws_123", USER_ID, TRACE_ID);
    }

    @Test
    void superAdministratorCanRefreshAllApplicationGitGroupsWithoutAssignedOpencodeProcess() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.refreshApplicationGit("app_gcms", USER_ID, true, TRACE_ID))
                .thenReturn(new ApplicationGitRefreshResponse(
                        "app_gcms",
                        "F-GCMS",
                        1,
                        1,
                        0,
                        0,
                        List.of(new ApplicationGitRefreshGroupResponse(
                                "awv_123",
                                "repo_123",
                                "GCMS",
                                "20260707",
                                "feature_testagent_20260707",
                                2,
                                "commit_before",
                                "commit_after",
                                "UPDATED",
                                null,
                                "已刷新 feature，并触发相关 worktree 收敛"))));
        UserOpencodeProcessAssignmentService assignmentService = org.mockito.Mockito.mock(
                UserOpencodeProcessAssignmentService.class);

        client(service, assignmentService, List.of("SUPER_ADMIN")).post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/git-refresh")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.updatedGroups").isEqualTo(1)
                .jsonPath("$.data.groups[0].workspaceCount").isEqualTo(2)
                .jsonPath("$.data.groups[0].status").isEqualTo("UPDATED");

        verify(service).refreshApplicationGit("app_gcms", USER_ID, true, TRACE_ID);
        org.mockito.Mockito.verifyNoInteractions(assignmentService);
    }

    @Test
    void superAdministratorCanRefreshOneApplicationGitGroupWithoutAssignedOpencodeProcess() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.refreshApplicationGitGroup(
                "app_gcms",
                "repo_123",
                "20260707",
                "feature_testagent_20260707",
                USER_ID,
                true,
                TRACE_ID))
                .thenReturn(new ApplicationGitRefreshResponse(
                        "app_gcms",
                        "F-GCMS",
                        1,
                        0,
                        1,
                        0,
                        List.of(new ApplicationGitRefreshGroupResponse(
                                "awv_123",
                                "repo_123",
                                "GCMS",
                                "20260707",
                                "feature_testagent_20260707",
                                1,
                                "commit_current",
                                "commit_current",
                                "UP_TO_DATE",
                                null,
                                "feature 已是远端最新，已重新触发相关 worktree 收敛"))));
        UserOpencodeProcessAssignmentService assignmentService = org.mockito.Mockito.mock(
                UserOpencodeProcessAssignmentService.class);

        client(service, assignmentService, List.of("SUPER_ADMIN")).post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/git-refresh-groups")
                .header("X-Trace-Id", TRACE_ID)
                .bodyValue(Map.of(
                        "repositoryId", "repo_123",
                        "version", "20260707",
                        "branch", "feature_testagent_20260707"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.totalGroups").isEqualTo(1)
                .jsonPath("$.data.groups[0].branch").isEqualTo("feature_testagent_20260707");

        verify(service).refreshApplicationGitGroup(
                "app_gcms",
                "repo_123",
                "20260707",
                "feature_testagent_20260707",
                USER_ID,
                true,
                TRACE_ID);
        org.mockito.Mockito.verifyNoInteractions(assignmentService);
    }

    @Test
    void superAdministratorCanPreviewWorkspaceBranchesWithoutAssignedOpencodeProcess() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.listApplicationGitRefreshScopes(USER_ID, true)).thenReturn(List.of(
                new ApplicationGitRefreshScopeResponse(
                        "app_gcms",
                        "F-GCMS",
                        true,
                        1,
                        List.of(new ApplicationGitRefreshScopeGroupResponse(
                                "repo_123",
                                "GCMS",
                                "20260707",
                                "feature_testagent_20260707",
                                1,
                                List.of(new ApplicationGitRefreshScopeWorkspaceResponse(
                                        "awv_123",
                                        "aws_123",
                                        "登录测试",
                                        "F-GCMS/login",
                                        true)))))));
        UserOpencodeProcessAssignmentService assignmentService = org.mockito.Mockito.mock(
                UserOpencodeProcessAssignmentService.class);

        client(service, assignmentService, List.of("SUPER_ADMIN")).get()
                .uri("/api/internal/platform/workspace-management/applications/git-refresh-scopes")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].groups[0].branch").isEqualTo("feature_testagent_20260707")
                .jsonPath("$.data[0].groups[0].workspaces[0].workspaceName").isEqualTo("登录测试");

        verify(service).listApplicationGitRefreshScopes(USER_ID, true);
        org.mockito.Mockito.verifyNoInteractions(assignmentService);
    }

    @Test
    void applicationAdministratorCanUseMemberScopedGitRefreshEndpoints() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.listApplicationGitRefreshScopes(USER_ID, false)).thenReturn(List.of());
        when(service.refreshApplicationGit("app_gcms", USER_ID, false, TRACE_ID))
                .thenReturn(new ApplicationGitRefreshResponse(
                        "app_gcms", "F-GCMS", 0, 0, 0, 0, List.of()));
        when(service.refreshApplicationGitGroup(
                "app_gcms",
                "repo_123",
                "20260707",
                "feature_testagent_20260707",
                USER_ID,
                false,
                TRACE_ID))
                .thenReturn(new ApplicationGitRefreshResponse(
                        "app_gcms", "F-GCMS", 0, 0, 0, 0, List.of()));
        WebTestClient appAdmin = client(
                service,
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class),
                List.of("APP_ADMIN"));

        appAdmin.get()
                .uri("/api/internal/platform/workspace-management/applications/git-refresh-scopes")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk();
        appAdmin.post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/git-refresh")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk();
        appAdmin.post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/git-refresh-groups")
                .header("X-Trace-Id", TRACE_ID)
                .bodyValue(Map.of(
                        "repositoryId", "repo_123",
                        "version", "20260707",
                        "branch", "feature_testagent_20260707"))
                .exchange()
                .expectStatus().isOk();

        verify(service).listApplicationGitRefreshScopes(USER_ID, false);
        verify(service).refreshApplicationGit("app_gcms", USER_ID, false, TRACE_ID);
        verify(service).refreshApplicationGitGroup(
                "app_gcms",
                "repo_123",
                "20260707",
                "feature_testagent_20260707",
                USER_ID,
                false,
                TRACE_ID);
    }

    @Test
    void ordinaryUserCannotRefreshApplicationGit() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);

        client(service).post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/git-refresh")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void ordinaryUserCannotRefreshOneApplicationGitGroup() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);

        client(service).post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/git-refresh-groups")
                .header("X-Trace-Id", TRACE_ID)
                .bodyValue(Map.of(
                        "repositoryId", "repo_123",
                        "version", "20260707",
                        "branch", "feature_testagent_20260707"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void ordinaryUserCannotPreviewApplicationGitBranches() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);

        client(service).get()
                .uri("/api/internal/platform/workspace-management/applications/git-refresh-scopes")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void versionGitAccessCheckPassesCurrentUserWithoutCreatingWorkspace() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.checkVersionGitAccess("awv_123", USER_ID))
                .thenReturn(new GitRepositoryAccessResponse(
                        false,
                        "repo_123",
                        "GCMS 测试版本库",
                        "feature_testagent_20260707",
                        "REPOSITORY_PERMISSION_REQUIRED"));

        client(service).get()
                .uri("/api/internal/platform/workspace-management/workspace-versions/awv_123/git-access")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.accessible").isEqualTo(false)
                .jsonPath("$.data.repositoryId").isEqualTo("repo_123")
                .jsonPath("$.data.repositoryName").isEqualTo("GCMS 测试版本库")
                .jsonPath("$.data.reason").isEqualTo("REPOSITORY_PERMISSION_REQUIRED");

        verify(service).checkVersionGitAccess("awv_123", USER_ID);
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
    void applicationAdministratorCanCompleteResolvedWorkspaceMerge() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.completeWorkspaceGitMerge("wks_123", USER_ID, TRACE_ID))
                .thenReturn(new WorkspaceGitMergeCompletionResponse("MERGED", "merge-head", "feature-head"));

        client(service, readyAssignmentService("127.0.0.1"), List.of("APP_ADMIN")).post()
                .uri("/api/internal/platform/workspace-management/workspaces/wks_123/git-conflict/complete")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("MERGED")
                .jsonPath("$.data.headCommit").isEqualTo("merge-head");

        verify(service).completeWorkspaceGitMerge("wks_123", USER_ID, TRACE_ID);
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
    void experienceWorkspaceCommitUsesWorkspaceEndpointWithoutPublish() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.commitExperienceWorkspace(
                "wrk_exp_local", "docs: 更新说明", List.of("README.md"), USER_ID))
                .thenReturn(new WorkspaceGitCommitResponse(
                        "LOCAL_COMMITTED", "wrk_exp_local", "commit_local", "已本地提交"));

        client(service).post()
                .uri("/api/internal/platform/workspace-management/workspaces/wrk_exp_local/git-commit")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"commitMessage":"docs: 更新说明","files":["README.md"]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("LOCAL_COMMITTED")
                .jsonPath("$.data.headCommit").isEqualTo("commit_local");

        verify(service).commitExperienceWorkspace(
                "wrk_exp_local", "docs: 更新说明", List.of("README.md"), USER_ID);
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
