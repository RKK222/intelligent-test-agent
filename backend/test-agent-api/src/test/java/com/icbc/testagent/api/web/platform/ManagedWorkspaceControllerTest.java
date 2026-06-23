package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.workspace.ManagedWorkspaceApplicationService;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.ManagedApplicationResponse;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.WorkspaceRuntimeResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-23T00:00:00Z"));
        when(service.createVersion(eq("app_gcms"), eq("aws_123"), eq("20260707"), eq("feature_testagent_20260707"), eq(USER_ID), eq(TRACE_ID)))
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

        client(service).post()
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
    void markRecentWorkspaceReturnsRuntimeWorkspace() {
        ManagedWorkspaceApplicationService service = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(service.markRecentWorkspace("wks_123", USER_ID))
                .thenReturn(new WorkspaceRuntimeResponse(
                        "wks_123",
                        "F-GCMS-20260707",
                        "/data/workspace",
                        "ACTIVE",
                        Instant.parse("2026-06-23T00:00:00Z"),
                        Instant.parse("2026-06-23T00:00:00Z")));

        client(service).post()
                .uri("/api/internal/platform/workspace-management/workspaces/wks_123/recent")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workspaceId").isEqualTo("wks_123");

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

    private WebTestClient client(ManagedWorkspaceApplicationService service) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "888888888",
                "888888888",
                List.of("USER"),
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        return WebTestClient.bindToController(new ManagedWorkspaceController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
