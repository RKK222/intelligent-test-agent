package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloadResult;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutServerStatus;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutStatus;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutTargetStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
import com.enterprise.testagent.workspace.AgentConfigResponses;
import com.enterprise.testagent.workspace.AgentConfigResponses.AgentConfigWorktreeOptionResponse;
import com.enterprise.testagent.workspace.AgentConfigResponses.AgentConfigWorktreeResponse;
import com.enterprise.testagent.workspace.AgentConfigResponses.PublicRepositoryStatusResponse;
import com.enterprise.testagent.workspace.AgentConfigResponses.AgentConfigStatusResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.scheduler.Schedulers;

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
    void superAdminCanReadGlobalPublicRolloutServerErrors() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.latestPublicRolloutStatus()).thenReturn(Optional.of(new PublicAgentConfigRolloutStatus(
                "acr_rollout",
                "DRAINING",
                "main",
                "abc123",
                null,
                "acr_previous",
                null,
                "修复错误配置",
                Instant.parse("2026-07-28T01:00:00Z"),
                Instant.parse("2026-07-28T01:01:00Z"),
                null,
                List.of(new PublicAgentConfigRolloutServerStatus(
                        "linux-2", "RETRY_WAIT", 2, 3, 1, 2, 0,
                        0, 0, 0,
                        "公共 Agent 运行副本存在未提交变更",
                        null,
                        Instant.parse("2026-07-28T01:01:00Z"),
                        List.of(new PublicAgentConfigRolloutTargetStatus(
                                "act_pending",
                                "usr_1",
                                "张三",
                                "linux-2",
                                "container-1",
                                4096,
                                123L,
                                Instant.parse("2026-07-28T00:59:00Z"),
                                "RETRY_WAIT",
                                3,
                                Instant.parse("2026-07-28T01:01:05Z"),
                                "SESSION_RUNNING",
                                false,
                                Instant.parse("2026-07-28T01:01:00Z"))))))));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/workspace-management/agent-config/public/rollout")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("DRAINING")
                .jsonPath("$.data.supersedesRolloutId").isEqualTo("acr_previous")
                .jsonPath("$.data.supersedeReason").isEqualTo("修复错误配置")
                .jsonPath("$.data.servers[0].linuxServerId").isEqualTo("linux-2")
                .jsonPath("$.data.servers[0].targetPending").isEqualTo(1)
                .jsonPath("$.data.servers[0].pendingTargets[0].username").isEqualTo("张三")
                .jsonPath("$.data.servers[0].pendingTargets[0].lastError").isEqualTo("SESSION_RUNNING")
                .jsonPath("$.data.servers[0].lastError").isEqualTo("公共 Agent 运行副本存在未提交变更");
    }

    @Test
    void nonSuperAdminCannotReadGlobalPublicRollout() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/workspace-management/agent-config/public/rollout")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(service);
    }

    @Test
    void superAdminCanReadApplicationRolloutProgress() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.recentApplicationRolloutStatuses()).thenReturn(List.of(new PublicAgentConfigRolloutStatus(
                "acr_application",
                "DRAINING",
                "release/20260820",
                "commit-tool",
                null,
                null,
                null,
                null,
                Instant.parse("2026-08-20T06:00:00Z"),
                Instant.parse("2026-08-20T06:01:00Z"),
                null,
                List.of(),
                AgentConfigRolloutScope.APPLICATION,
                "awv_1")));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/workspace-management/agent-config/application/rollouts")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].rolloutId").isEqualTo("acr_application")
                .jsonPath("$.data[0].configScope").isEqualTo("APPLICATION")
                .jsonPath("$.data[0].scopeKey").isEqualTo("awv_1");

        verify(service).recentApplicationRolloutStatuses();
    }

    @Test
    void nonSuperAdminCannotReadApplicationRolloutProgress() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/workspace-management/agent-config/application/rollouts")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(service);
    }

    @Test
    void superAdminCanTriggerMissingPublicWorktreeCompensationOnRequestedServer() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.reconcileMissingPublicWorktrees("127.0.0.1", TRACE_ID)).thenReturn(
                new com.enterprise.testagent.workspace.AgentConfigResponses.PublicWorktreeCompensationResponse(
                        "127.0.0.1",
                        "COMPLETED",
                        1,
                        1,
                        1,
                        0,
                        List.of(new com.enterprise.testagent.workspace.AgentConfigResponses.PublicWorktreeCompensationItemResponse(
                                USER_ID.value(),
                                "SUCCEEDED",
                                "agw_compensated",
                                null,
                                "公共个人 worktree 已创建或复用")),
                        "补偿执行完成",
                        Instant.parse("2026-08-11T02:00:00Z")));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/worktrees/reconcile")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"linuxServerId":"127.0.0.1"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("COMPLETED")
                .jsonPath("$.data.succeededCount").isEqualTo(1)
                .jsonPath("$.data.items[0].userId").isEqualTo(USER_ID.value())
                .jsonPath("$.data.items[0].worktreeId").isEqualTo("agw_compensated");

        verify(service).reconcileMissingPublicWorktrees("127.0.0.1", TRACE_ID);
    }

    @Test
    void nonSuperAdminCannotTriggerMissingPublicWorktreeCompensation() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/worktrees/reconcile")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"linuxServerId":"127.0.0.1"}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(service);
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
    void nonSuperAdminCannotSupersedePublicRollout() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/rollout/supersede")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"activeRolloutId":"acr_stuck","branch":"feature_config","reason":"修复错误配置"}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(service);
    }

    @Test
    void superAdminCanForceStopAndSupersedeExpectedPublicRollout() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/rollout/supersede")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "activeRolloutId":"acr_stuck",
                          "branch":"feature_config",
                          "operationId":"aco_supersede_12345678",
                          "discardLocalChanges":true,
                          "reason":"修复 description 为空"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).supersedePublicConfigRollout(
                "acr_stuck",
                "feature_config",
                "aco_supersede_12345678",
                true,
                "修复 description 为空",
                USER_ID,
                TRACE_ID);
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
        when(service.localPublicRepositoryStatus(USER_ID)).thenReturn(new PublicRepositoryStatusResponse(
                "127.0.0.1", "local", "/config", "/config/opencode", "/worktrees", "CONFLICT",
                true, false, "main", "abc", "本机修改将被提交", true));
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
    void dirtySharedRuntimeIsRejectedBeforeStartingGlobalRolloutWithoutExplicitConfirmation() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        AgentConfigBackendRoutingService routingService = org.mockito.Mockito.mock(AgentConfigBackendRoutingService.class);
        when(routingService.listPublicRepositories(any(), eq(TRACE_ID))).thenReturn(List.of(
                new PublicRepositoryStatusResponse(
                        "linux-2",
                        "server-2",
                        "/data/agent-opencode/.config",
                        "/data/agent-opencode/.config/opencode",
                        "/data/agent-opencode/.configdev",
                        "CONFLICT",
                        true,
                        false,
                        "main",
                        "abc123",
                        "Git 工作树存在未提交变更",
                        true)));
        WebTestClient client = client(
                service,
                ticketService(new AgentConfigOperationTicketStore()),
                routingService,
                org.mockito.Mockito.mock(AgentConfigFileRoutingService.class),
                List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/update")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"branch":"main","operationId":"aco_12345678","discardLocalChanges":false}
                        """)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.details.linuxServerIds[0]").isEqualTo("linux-2")
                .jsonPath("$.details.discardLocalChangesAllowed").isEqualTo(true);

        verify(service, never()).updatePublicConfig(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void publicPushChecksRemoteSharedChangesBeforeAnyGitMutation() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        AgentConfigBackendRoutingService routing = org.mockito.Mockito.mock(AgentConfigBackendRoutingService.class);
        when(routing.currentLinuxServerId()).thenReturn("linux-1");
        when(routing.listPublicRepositories(any(), eq(TRACE_ID))).thenReturn(List.of(
                new PublicRepositoryStatusResponse("linux-2", "remote", "/config", "/config/opencode",
                        "/worktrees", "CONFLICT", true, false, "main", "abc", "共享副本有修改", true)));
        WebTestClient client = client(service, ticketService(new AgentConfigOperationTicketStore()), routing,
                org.mockito.Mockito.mock(AgentConfigFileRoutingService.class), List.of(Dictionary.ROLE_SUPER_ADMIN));
        for (String endpoint : List.of("publish", "update-and-push")) {
            client.post().uri("/api/internal/platform/workspace-management/agent-config/public/" + endpoint)
                    .header("X-Trace-Id", TRACE_ID).contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"worktreeId\":\"agw_1\",\"branch\":\"main\",\"discardLocalChanges\":true}")
                    .exchange().expectStatus().isEqualTo(409).expectBody()
                    .jsonPath("$.details.linuxServerIds[0]").isEqualTo("linux-2");
        }
        verifyNoInteractions(service);
    }

    @Test
    void publicPushRejectsUnavailableReplicaButAllowsCleanReplicas() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        AgentConfigBackendRoutingService routing = org.mockito.Mockito.mock(AgentConfigBackendRoutingService.class);
        when(routing.listPublicRepositories(any(), eq(TRACE_ID))).thenReturn(List.of(
                new PublicRepositoryStatusResponse("linux-2", "remote", null, null, null,
                        "UNAVAILABLE", false, false, null, null, "连接失败")));
        WebTestClient client = client(service, ticketService(new AgentConfigOperationTicketStore()), routing,
                org.mockito.Mockito.mock(AgentConfigFileRoutingService.class), List.of(Dictionary.ROLE_SUPER_ADMIN));
        client.post().uri("/api/internal/platform/workspace-management/agent-config/public/publish")
                .header("X-Trace-Id", TRACE_ID).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"worktreeId\":\"agw_1\",\"operationId\":\"aco_1\"}")
                .exchange().expectStatus().isEqualTo(503);
        verifyNoInteractions(service);
        when(routing.listPublicRepositories(any(), eq(TRACE_ID))).thenReturn(List.of(
                new PublicRepositoryStatusResponse("linux-2", "remote", "/config", "/config/opencode", "/worktrees",
                        "READY", true, false, "main", "abc", null, false)));
        client.post().uri("/api/internal/platform/workspace-management/agent-config/public/publish")
                .header("X-Trace-Id", TRACE_ID).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"worktreeId\":\"agw_1\",\"operationId\":\"aco_1\"}")
                .exchange().expectStatus().isOk();
        verify(service).publicPublish("agw_1", "aco_1", USER_ID, TRACE_ID);
    }

    @Test
    void onlySuperAdminCanResumeExactPublicRolloutWithExplicitDiscard() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/rollout/resume-sync")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"rolloutId\":\"acr_original\"}")
                .exchange().expectStatus().isForbidden();
        verifyNoInteractions(service);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));
        for (boolean discard : List.of(false, true)) {
            client.post().uri("/api/internal/platform/workspace-management/agent-config/public/rollout/resume-sync")
                    .header("X-Trace-Id", TRACE_ID).contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of("rolloutId", "acr_original", "discardLocalChanges", discard))
                    .exchange().expectStatus().isOk();
            verify(service).resumePublicConfigSync("acr_original", discard, USER_ID, TRACE_ID);
        }
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
    void nonSuperAdminCannotInitializeOrPullTargetPublicRepository() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/repositories/127.0.0.1/initialize")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"branch":"main","operationId":"aco_init_12345678"}
                        """)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/public/repositories/127.0.0.1/pull")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"branch":"main","operationId":"aco_pull_12345678"}
                        """)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        verifyNoInteractions(service);
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
                .thenAnswer(ignored -> {
                    if (Schedulers.isInNonBlockingThread()) {
                        throw new IllegalStateException("runtime reload must not run on the WebFlux event thread");
                    }
                    return new PersonalAgentConfigRuntimeReloadResult(true, "reloaded");
                });
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
    void regularUserCanDiscardOwnedPublicAgentFiles() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of());

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
    void regularUserCanReadOwnedPublicAgentDiff() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.publicDiff("agw_public", USER_ID))
                .thenReturn(new AgentConfigResponses.AgentConfigDiffResponse(List.of(), false));
        WebTestClient client = client(service, List.of());

        client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/platform/workspace-management/agent-config/public/diff")
                        .queryParam("worktreeId", "agw_public")
                        .build())
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.files").isEmpty();

        verify(service).publicDiff("agw_public", USER_ID);
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
    void regularMemberCanDiscardOwnWorkspaceAgentFiles() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        WebTestClient client = client(service, List.of());

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/workspaces/wrk_project/discard")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"files":["agents/review.md"],"worktreeId":null}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).workspaceDiscardForPersonalMember(
                "wrk_project",
                List.of("agents/review.md"),
                null,
                USER_ID,
                TRACE_ID);
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
