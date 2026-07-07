package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.configuration.management.ConfigurationManagementApplicationService;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.ApplicationMemberResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.ApplicationResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.CodeRepositoryResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.RepositoryDeploymentOptionResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.RepositoryDeploymentOptionsResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.RepositoryTreeNodeResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.RepositoryTreeResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.RepositoryTypeOptionResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.SshKeyResponse;
import com.icbc.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.icbc.testagent.domain.configuration.CodeRepositoryType;
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
    void nonAdminCanAccessApplicationManagement() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.listApplications(true)).thenReturn(List.of(new ApplicationResponse("app_gcms", "F-GCMS", true)));
        WebTestClient client = client(service, List.of());

        client.get()
                .uri("/api/internal/platform/configuration-management/applications?enabled=true")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].appId").isEqualTo("app_gcms");
    }

    @Test
    void nonAdminCanAddSelfAsApplicationMember() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.addMember("app_gcms", USER_ID.value())).thenReturn(new ApplicationMemberResponse(
                USER_ID.value(),
                "admin",
                "AUTH_1",
                null,
                null,
                null));
        WebTestClient client = client(service, List.of());

        client.post()
                .uri("/api/internal/platform/configuration-management/applications/app_gcms/members")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"usr_1234567890abcdef"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo(USER_ID.value());

        verify(service).addMember("app_gcms", USER_ID.value());
    }

    @Test
    void nonAdminCannotAddAnotherApplicationMember() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        WebTestClient client = client(service, List.of());

        client.post()
                .uri("/api/internal/platform/configuration-management/applications/app_gcms/members")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"usr_other_1234567890"}
                        """)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.details.appId").isEqualTo("app_gcms")
                .jsonPath("$.details.targetUserId").isEqualTo("usr_other_1234567890");

        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).addMember(eq("app_gcms"), anyString());
    }

    @Test
    void superAdminCanAddAnotherApplicationMember() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.addMember("app_gcms", "usr_other_1234567890")).thenReturn(new ApplicationMemberResponse(
                "usr_other_1234567890",
                "other",
                "AUTH_2",
                null,
                null,
                null));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/configuration-management/applications/app_gcms/members")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"usr_other_1234567890"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo("usr_other_1234567890");

        verify(service).addMember("app_gcms", "usr_other_1234567890");
    }

    @Test
    void appAdminCanListRepositoryTypesFromDictionary() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.listRepositoryTypes()).thenReturn(List.of(
                new RepositoryTypeOptionResponse(CodeRepositoryType.TEST_WORK_REPOSITORY.value(), "测试工作库"),
                new RepositoryTypeOptionResponse(CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(), "应用代码库"),
                new RepositoryTypeOptionResponse(CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value(), "应用资产库")));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/repository-types")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].typeCode").isEqualTo(CodeRepositoryType.TEST_WORK_REPOSITORY.value())
                .jsonPath("$.data[0].typeLabel").isEqualTo("测试工作库");
    }

    @Test
    void createRepositoryAcceptsRepositoryTypeAndKeepsLegacyStandardInResponse() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.createRepository(
                eq("https://gitee.com/demo/repo.git"),
                eq("演示库"),
                eq("demo"),
                eq(false),
                eq(CodeRepositoryType.TEST_WORK_REPOSITORY.value()),
                eq(CodeRepositoryDeploymentMode.INTERNAL.value())))
                .thenReturn(new CodeRepositoryResponse(
                        "repo_123",
                        "https://gitee.com/demo/repo.git",
                        "演示库",
                        "demo",
                        CodeRepositoryDeploymentMode.INTERNAL.value(),
                        CodeRepositoryType.TEST_WORK_REPOSITORY.value(),
                        "测试工作库",
                        true,
                        Instant.parse("2026-07-02T08:00:00Z"),
                        Instant.parse("2026-07-02T08:00:00Z")));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.post()
                .uri("/api/internal/platform/configuration-management/repositories")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"gitUrl":"https://gitee.com/demo/repo.git","name":"演示库","englishName":"demo","standard":false,"repositoryType":"TEST_WORK_REPOSITORY","deploymentMode":"INTERNAL"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.deploymentMode").isEqualTo(CodeRepositoryDeploymentMode.INTERNAL.value())
                .jsonPath("$.data.repositoryType").isEqualTo(CodeRepositoryType.TEST_WORK_REPOSITORY.value())
                .jsonPath("$.data.repositoryTypeLabel").isEqualTo("测试工作库")
                .jsonPath("$.data.standard").isEqualTo(true);
    }

    @Test
    void repositoryDeploymentOptionsReturnCurrentUserInternalSshPrefix() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.repositoryDeploymentOptions(USER_ID))
                .thenReturn(new RepositoryDeploymentOptionsResponse(
                        CodeRepositoryDeploymentMode.INTERNAL.value(),
                        "ssh://AUTH_1@",
                        List.of(
                                new RepositoryDeploymentOptionResponse(CodeRepositoryDeploymentMode.EXTERNAL.value(), "外部部署"),
                                new RepositoryDeploymentOptionResponse(CodeRepositoryDeploymentMode.INTERNAL.value(), "内部部署"))));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/repository-deployment-options")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.defaultDeploymentMode").isEqualTo(CodeRepositoryDeploymentMode.INTERNAL.value())
                .jsonPath("$.data.internalSshPrefix").isEqualTo("ssh://AUTH_1@")
                .jsonPath("$.data.options[1].mode").isEqualTo(CodeRepositoryDeploymentMode.INTERNAL.value());
    }

    @Test
    void appAdminCanLoadApplicationRepositoryRemoteTree() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.listRepositoryTree("app_gcms", "repo_123", "feature_testagent_20260707", USER_ID))
                .thenReturn(new RepositoryTreeResponse(List.of(new RepositoryTreeNodeResponse(
                        "F-COSS",
                        "F-COSS",
                        "directory",
                        List.of(new RepositoryTreeNodeResponse("W1", "F-COSS/W1", "directory", List.of()))))));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/applications/app_gcms/repositories/repo_123/tree?branch=feature_testagent_20260707")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.nodes[0].name").isEqualTo("F-COSS")
                .jsonPath("$.data.nodes[0].children[0].path").isEqualTo("F-COSS/W1");
    }

    @Test
    void personalSshKeyResponseDoesNotExposePrivateKey() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.addSshKey(eq(USER_ID), eq("work"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new SshKeyResponse("ssh_123", "work", "SHA256:abc", Instant.parse("2026-06-23T00:00:00Z")));
        WebTestClient client = client(service, List.of());

        client.post()
                .uri("/api/internal/platform/configuration-management/personal/ssh-keys")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"work","encryptedPrivateKey":"enc","encryptedAesKey":"aes","encryptionNonce":"nonce","fingerprint":"SHA256:abc"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.sshKeyId").isEqualTo("ssh_123")
                .jsonPath("$.data.fingerprint").isEqualTo("SHA256:abc")
                .jsonPath("$.data.privateKey").doesNotExist();
    }

    @Test
    void sshKeyPublicKeyEndpointReturnsSpkiBase64() {
        WebTestClient client = client(
                org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class),
                org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class),
                org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class),
                List.of());

        client.get()
                .uri("/api/internal/platform/configuration-management/ssh-key/public-key")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.publicKey").isNotEmpty();
    }

    @Test
    void createWorkspaceUsesCurrentUserReadyOpencodeServer() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        ManagedWorkspaceApplicationService workspaceService = org.mockito.Mockito.mock(ManagedWorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = readyAssignmentService("10.8.0.12");
        when(workspaceService.createWorkspaceAccepted(
                eq("app_gcms"),
                eq("repo_123"),
                eq("feature_testagent_20260707"),
                eq("src/main"),
                eq("主工作区"),
                eq(true),
                eq(null),
                eq("wco_12345678"),
                eq(USER_ID),
                eq("10.8.0.12"),
                eq(TRACE_ID)))
                .thenReturn(new com.icbc.testagent.workspace.ManagedWorkspaceResponses.CreateWorkspaceAcceptedResponse(
                        "wco_12345678",
                        "ACCEPTED",
                        Instant.parse("2026-06-26T00:00:00Z")));
        WebTestClient client = client(service, workspaceService, assignmentService, List.of(Dictionary.ROLE_APP_ADMIN));

        client.post()
                .uri("/api/internal/platform/configuration-management/applications/app_gcms/workspaces")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"repositoryId":"repo_123","branch":"feature_testagent_20260707","directoryPath":"src/main","workspaceName":"主工作区","directoryNew":true,"operationId":"wco_12345678"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.operationId").isEqualTo("wco_12345678")
                .jsonPath("$.data.status").isEqualTo("ACCEPTED");

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
                        assignmentService,
                        new com.icbc.testagent.common.git.RsaKeyService()))
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
                Instant.parse("2026-06-26T00:00:00Z"),
                null,
                null,
                null);
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
