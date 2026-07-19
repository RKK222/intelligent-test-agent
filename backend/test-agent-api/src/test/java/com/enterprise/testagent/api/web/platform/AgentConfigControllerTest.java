package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloadResult;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
import com.enterprise.testagent.workspace.AgentConfigResponses.AgentConfigWorktreeOptionResponse;
import com.enterprise.testagent.workspace.AgentConfigResponses.AgentConfigWorktreeResponse;
import com.enterprise.testagent.workspace.AgentConfigResponses.PublicRepositoryStatusResponse;
import com.enterprise.testagent.workspace.AgentConfigResponses.AgentConfigStatusResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class AgentConfigControllerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void authenticatedUserCanReadPublicStatus() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.publicStatus(false, USER_ID)).thenReturn(new AgentConfigStatusResponse(
                "PUBLIC",
                false,
                false,
                "UNCONFIGURED",
                "/data/.testagent/agent-opencode/.config",
                "/data/.testagent/agent-opencode/.config/opencode/agents",
                null,
                null));
        WebTestClient client = client(service, List.of());

        client.get()
                .uri("/api/internal/platform/workspace-management/agent-config/public/status")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.scope").isEqualTo("PUBLIC")
                .jsonPath("$.data.writable").isEqualTo(false)
                .jsonPath("$.data.gitUrl").isEqualTo("UNCONFIGURED");
    }

    @Test
    void nonSuperAdminCannotUpdatePublicConfig() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/update")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"branch":"main","operationId":"aco_12345678"}
                        """)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        verifyNoInteractions(service);
    }

    @Test
    void nonSuperAdminCannotUpdateAndPushPublicConfig() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/update-and-push")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"branch":"main","commitMessage":"chore: sync","operationId":"aco_12345678"}
                        """)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        verifyNoInteractions(service);
    }

    @Test
    void superAdminCanUpdateAndPushPublicConfigWithExplicitDiscard() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/update-and-push")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"branch":"main","commitMessage":"chore: sync public agent docs","operationId":"aco_12345678","discardLocalChanges":true}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).updatePublicConfigAndPush(
                "main",
                "chore: sync public agent docs",
                "aco_12345678",
                true,
                USER_ID,
                TRACE_ID);
    }

    @Test
    void superAdminCanExplicitlyDiscardLocalChangesWhenUpdatingPublicConfig() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/update")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"branch":"main","operationId":"aco_12345678","discardLocalChanges":true}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).updatePublicConfig(
                "main",
                "aco_12345678",
                true,
                USER_ID,
                TRACE_ID);
    }

    @Test
    void superAdminCanPullTargetPublicRepository() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.updatePublicConfig(
                "master",
                "aco_pull_12345678",
                false,
                USER_ID,
                TRACE_ID)).thenReturn(new com.enterprise.testagent.workspace.AgentConfigResponses.AgentConfigOperationResponse(
                "aco_pull_12345678",
                "PUBLIC",
                null,
                "update",
                "SUCCEEDED",
                "BROADCASTING",
                null,
                null,
                "master",
                "commit_latest",
                TRACE_ID,
                Instant.parse("2026-06-25T00:00:00Z"),
                Instant.parse("2026-06-25T00:00:01Z")));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/repositories/127.0.0.1/pull")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"branch":"master","operationId":"aco_pull_12345678","discardLocalChanges":false}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).updatePublicConfig(
                "master",
                "aco_pull_12345678",
                false,
                USER_ID,
                TRACE_ID);
    }

    @Test
    void superAdminCanReadLocalPublicRepositoryStatus() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.localPublicRepositoryStatus(USER_ID)).thenReturn(new PublicRepositoryStatusResponse(
                "127.0.0.1",
                "127.0.0.1",
                "/data/.testagent/agent-opencode/.config",
                "/data/.testagent/agent-opencode/.config/opencode",
                "/data/.testagent/agent-opencode/.configdev",
                "READY",
                true,
                true,
                "main",
                "commit_1",
                null));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/workspace-management/agent-config/public/repositories/local")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.linuxServerId").isEqualTo("127.0.0.1")
                .jsonPath("$.data.initialized").isEqualTo(true)
                .jsonPath("$.data.currentBranch").isEqualTo("main");
    }

    @Test
    void createPublicWorktreePassesSelectedLinuxServerIdToService() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.createPublicWorktree(
                "change-agent-md",
                "main",
                "aco_12345678",
                "127.0.0.1",
                USER_ID,
                TRACE_ID)).thenReturn(new AgentConfigWorktreeResponse(
                        "agw_123",
                        "PUBLIC",
                        null,
                        "127.0.0.1",
                        "change-agent-md-20260628",
                        "change-agent-md-20260628",
                        "/data/.testagent/agent-opencode/.configdev/change-agent-md-20260628",
                        "/data/.testagent/agent-opencode/.configdev/change-agent-md-20260628/opencode/agents",
                        "ACTIVE",
                        Instant.parse("2026-06-28T00:00:00Z"),
                        Instant.parse("2026-06-28T00:00:00Z")));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/worktrees")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"baseName":"change-agent-md","branch":"main","operationId":"aco_12345678","linuxServerId":"127.0.0.1"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.linuxServerId").isEqualTo("127.0.0.1");

        verify(service).createPublicWorktree("change-agent-md", "main", "aco_12345678", "127.0.0.1", USER_ID, TRACE_ID);
    }

    @Test
    void superAdminCanListPublicWorktreesByLinuxServer() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.listPublicWorktrees("127.0.0.1", USER_ID)).thenReturn(List.of(
                new AgentConfigWorktreeOptionResponse(
                        "agw_123",
                        "PUBLIC",
                        null,
                        "127.0.0.1",
                        "change-agent-md",
                        "change-agent-md",
                        "/data/.testagent/agent-opencode/.configdev/change-agent-md",
                        "/data/.testagent/agent-opencode/.configdev/change-agent-md/opencode/agents",
                        "ACTIVE",
                        Instant.parse("2026-06-28T00:00:00Z"),
                        Instant.parse("2026-06-28T00:00:00Z"),
                        "usr_admin",
                        "admin")));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/workspace-management/agent-config/public/worktrees?linuxServerId=127.0.0.1")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].worktreeId").isEqualTo("agw_123")
                .jsonPath("$.data[0].createdByUserId").isEqualTo("usr_admin")
                .jsonPath("$.data[0].createdByUsername").isEqualTo("admin");

        verify(service).listPublicWorktrees("127.0.0.1", USER_ID);
    }

    @Test
    void nonSuperAdminCannotListPublicWorktrees() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/workspace-management/agent-config/public/worktrees?linuxServerId=127.0.0.1")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        verifyNoInteractions(service);
    }

    @Test
    void superAdminCanReloadOwnedPublicPersonalRuntime() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.reloadPublicPersonalRuntime("agw_public", USER_ID, TRACE_ID))
                .thenReturn(new PersonalAgentConfigRuntimeReloadResult(true, "reloaded"));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/runtime-reload")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"worktreeId":"agw_public","linuxServerId":"127.0.0.1"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.reloaded").isEqualTo(true)
                .jsonPath("$.data.message").isEqualTo("reloaded");

        verify(service).reloadPublicPersonalRuntime("agw_public", USER_ID, TRACE_ID);
    }

    @Test
    void nonSuperAdminCannotReloadPublicPersonalRuntime() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/runtime-reload")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"worktreeId":"agw_public","linuxServerId":"127.0.0.1"}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(service);
    }

    @Test
    void superAdminCanDiscardOwnedPublicAgentFiles() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/discard")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"files":["opencode/agents/review.md"],"worktreeId":"agw_public"}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).publicDiscard(List.of("opencode/agents/review.md"), "agw_public", USER_ID);
    }

    @Test
    void appAdminCanDiscardWorkspaceAgentFiles() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/workspaces/wrk_project/discard")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"files":["agents/review.md"],"worktreeId":null}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).workspaceDiscard("wrk_project", List.of("agents/review.md"), null, USER_ID);
    }

    @Test
    void publicWorktreeListRequiresLinuxServerId() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/workspace-management/agent-config/public/worktrees")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");

        verifyNoInteractions(service);
    }

    @Test
    void routesPublicAgentConfigFilesToTargetWebSocketBackend() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        AgentConfigFileRoutingService fileRoutingService = org.mockito.Mockito.mock(AgentConfigFileRoutingService.class);
        AgentConfigDtos.FileRouteRequest request = new AgentConfigDtos.FileRouteRequest(
                "PUBLIC",
                null,
                "agw_123",
                "linux-2");
        when(fileRoutingService.route(request)).thenReturn(new AgentConfigDtos.FileRouteResponse(
                "PUBLIC",
                null,
                "agw_123",
                "linux-2",
                "http://10.8.0.12:8080",
                "/api/internal/platform/workspace-management/file/ws",
                false,
                null));
        WebTestClient client = client(
                service,
                ticketService(new AgentConfigOperationTicketStore()),
                org.mockito.Mockito.mock(AgentConfigBackendRoutingService.class),
                fileRoutingService,
                List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/file-ws-route")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"scope":"PUBLIC","worktreeId":"agw_123","linuxServerId":"linux-2"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.scope").isEqualTo("PUBLIC")
                .jsonPath("$.data.linuxServerId").isEqualTo("linux-2")
                .jsonPath("$.data.webSocketPath").isEqualTo("/api/internal/platform/workspace-management/file/ws");

        verify(fileRoutingService).route(request);
        verifyNoInteractions(service);
    }

    @Test
    void createProgressTicketReturnsWebSocketUrl() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        AgentConfigOperationTicketStore ticketStore = new AgentConfigOperationTicketStore(
                Clock.fixed(Instant.parse("2026-06-26T00:00:00Z"), ZoneOffset.UTC),
                () -> "agt_fixedticket");
        WebTestClient client = client(service, ticketService(ticketStore), List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/operations/aco_12345678/tickets")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticket").isEqualTo("agt_fixedticket")
                .jsonPath("$.data.webSocketUrl")
                .isEqualTo("ws://122.233.30.114:8080/api/internal/platform/workspace-management/agent-config/operations/aco_12345678/ws?ticket=agt_fixedticket");
    }

    private WebTestClient client(AgentConfigApplicationService service, List<String> roles) {
        return client(service, ticketService(new AgentConfigOperationTicketStore()), roles);
    }

    private AgentConfigOperationTicketService ticketService(AgentConfigOperationTicketStore ticketStore) {
        BackendInstanceIdentity identity = org.mockito.Mockito.mock(BackendInstanceIdentity.class);
        when(identity.listenUrl()).thenReturn("http://122.233.30.114:8080");
        return new AgentConfigOperationTicketService(ticketStore, new CurrentBackendWebSocketUrlFactory(identity));
    }

    private WebTestClient client(
            AgentConfigApplicationService service,
            AgentConfigOperationTicketService ticketService,
            List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "admin",
                "AUTH_1",
                roles,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        return client(
                service,
                ticketService,
                new AgentConfigBackendRoutingService(service),
                org.mockito.Mockito.mock(AgentConfigFileRoutingService.class),
                roles);
    }

    private WebTestClient client(
            AgentConfigApplicationService service,
            AgentConfigOperationTicketService ticketService,
            AgentConfigBackendRoutingService routingService,
            AgentConfigFileRoutingService fileRoutingService,
            List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "admin",
                "AUTH_1",
                roles,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        return WebTestClient.bindToController(new AgentConfigController(service, ticketService, routingService, fileRoutingService))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
