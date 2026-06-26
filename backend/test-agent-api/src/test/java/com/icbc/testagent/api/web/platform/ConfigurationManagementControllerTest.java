package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.configuration.management.ConfigurationManagementApplicationService;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.ApplicationResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.SshKeyResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.workspace.ManagedWorkspaceApplicationService;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.ApplicationWorkspaceCreateResponse;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.WorkspaceCreateOperationResponse;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.WorkspaceCreateOperationStepResponse;
import com.icbc.testagent.workspace.ManagedWorkspaceResponses.WorkspaceRuntimeResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ConfigurationManagementControllerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void appAdminCanListEnabledApplications() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.listApplications(true)).thenReturn(List.of(new ApplicationResponse("app_gcms", "F-GCMS", true)));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/applications?enabled=true")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].appId").isEqualTo("app_gcms")
                .jsonPath("$.data[0].appName").isEqualTo("F-GCMS");
    }

    @Test
    void superAdminCanListEnabledApplications() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.listApplications(true)).thenReturn(List.of(new ApplicationResponse("app_gcms", "F-GCMS", true)));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/applications?enabled=true")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].appId").isEqualTo("app_gcms");
    }

    @Test
    void nonAdminCannotAccessApplicationManagement() {
        WebTestClient client = client(org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class), List.of());

        client.get()
                .uri("/api/internal/platform/configuration-management/applications?enabled=true")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void personalSshKeyResponseDoesNotExposePrivateKey() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.addSshKey(eq(USER_ID), eq("work"), anyString()))
                .thenReturn(new SshKeyResponse("ssh_123", "work", "SHA256:abc", Instant.parse("2026-06-23T00:00:00Z")));
        WebTestClient client = client(service, List.of());

        client.post()
                .uri("/api/internal/platform/configuration-management/personal/ssh-keys")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"work","privateKey":"-----BEGIN OPENSSH PRIVATE KEY-----\\nsecret\\n-----END OPENSSH PRIVATE KEY-----"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.sshKeyId").isEqualTo("ssh_123")
                .jsonPath("$.data.fingerprint").isEqualTo("SHA256:abc")
                .jsonPath("$.data.privateKey").doesNotExist();
    }

    @Test
    void createWorkspaceUsesCurrentUserReadyOpencodeServer() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        ManagedWorkspaceApplicationService workspaceService = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = readyAssignmentService("10.8.0.12");
        when(workspaceService.createApplicationWorkspaceWithInitialVersion(
                eq("app_gcms"),
                eq("repo_123"),
                eq("feature_testagent_20260707"),
                eq("src/main"),
                eq("主工作区"),
                eq(null),
                eq("wco_12345678"),
                eq(USER_ID),
                eq("10.8.0.12"),
                eq(TRACE_ID)))
                .thenReturn(workspaceCreateResponse());
        WebTestClient client = client(service, workspaceService, assignmentService, List.of(Dictionary.ROLE_APP_ADMIN));

        client.post()
                .uri("/api/internal/platform/configuration-management/applications/app_gcms/workspaces")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"repositoryId":"repo_123","branch":"feature_testagent_20260707","directoryPath":"src/main","workspaceName":"主工作区","operationId":"wco_12345678"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workspaceId").isEqualTo("awp_123")
                .jsonPath("$.data.initialVersion.versionId").isEqualTo("awv_123");

        verify(assignmentService).requireReadyProcess(USER_ID, "opencode", TRACE_ID);
    }

    @Test
    void appAdminCanQueryWorkspaceCreateOperation() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        ManagedWorkspaceApplicationService workspaceService = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        when(workspaceService.getWorkspaceCreateOperation("wco_12345678", USER_ID))
                .thenReturn(new WorkspaceCreateOperationResponse(
                        "wco_12345678",
                        "RUNNING",
                        "PREPARING_REPOSITORY",
                        null,
                        null,
                        "awp_123",
                        null,
                        List.of(new WorkspaceCreateOperationStepResponse("PREPARING_REPOSITORY", "下载代码", "RUNNING")),
                        Instant.parse("2026-06-26T00:00:00Z"),
                        Instant.parse("2026-06-26T00:00:01Z")));
        WebTestClient client = client(service, workspaceService, readyAssignmentService("10.8.0.12"), List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/workspace-create-operations/wco_12345678")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.operationId").isEqualTo("wco_12345678")
                .jsonPath("$.data.steps[0].code").isEqualTo("PREPARING_REPOSITORY");
    }

    private WebTestClient client(ConfigurationManagementApplicationService service, List<String> roles) {
        return client(
                service,
                org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class),
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class),
                roles);
    }

    private WebTestClient client(
            ConfigurationManagementApplicationService service,
            ManagedWorkspaceApplicationService workspaceService,
            UserOpencodeProcessAssignmentService assignmentService,
            List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "admin",
                "AUTH_1",
                roles,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        return WebTestClient.bindToController(new ConfigurationManagementController(
                        service,
                        workspaceService,
                        assignmentService))
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

    private static ApplicationWorkspaceCreateResponse workspaceCreateResponse() {
        WorkspaceRuntimeResponse runtime = new WorkspaceRuntimeResponse(
                "wks_123",
                "F-GCMS-20260707",
                "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/gcms/src/main",
                "ACTIVE",
                "10.8.0.12",
                Instant.parse("2026-06-26T00:00:00Z"),
                Instant.parse("2026-06-26T00:00:00Z"));
        ApplicationWorkspaceVersionResponse version = new ApplicationWorkspaceVersionResponse(
                "awv_123",
                "awp_123",
                "app_gcms",
                "repo_123",
                "20260707",
                "feature_testagent_20260707",
                "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/gcms",
                "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/gcms/src/main",
                runtime,
                "ACTIVE",
                null,
                null,
                null,
                null,
                Instant.parse("2026-06-26T00:00:00Z"),
                Instant.parse("2026-06-26T00:00:00Z"));
        return new ApplicationWorkspaceCreateResponse(
                "awp_123",
                "app_gcms",
                "repo_123",
                "feature_testagent_20260707",
                "src/main",
                "主工作区",
                version,
                Instant.parse("2026-06-26T00:00:00Z"),
                Instant.parse("2026-06-26T00:00:00Z"));
    }
}
