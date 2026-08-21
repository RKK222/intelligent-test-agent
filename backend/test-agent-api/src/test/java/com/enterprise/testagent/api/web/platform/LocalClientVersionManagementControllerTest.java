package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientUpdateCoordinator;
import com.enterprise.testagent.system.management.localclient.LocalClientReleaseCatalogClient;
import com.enterprise.testagent.system.management.localclient.LocalClientVersionPolicyApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 验证客户端版本管理的超管边界及单实例用户更新入口。 */
class LocalClientVersionManagementControllerTest {

    private static final UserId USER_ID = new UserId("usr_local_version_api");
    private static final String TRACE_ID = "trace_local_version_api";

    @Test
    void superAdminCanSyncReleasesAndCreateRollout() {
        LocalClientReleaseCatalogClient catalog = mock(LocalClientReleaseCatalogClient.class);
        LocalClientVersionPolicyApplicationService policies = mock(LocalClientVersionPolicyApplicationService.class);
        LocalClientUpdateCoordinator coordinator = mock(LocalClientUpdateCoordinator.class);
        when(catalog.sync()).thenReturn(new LocalClientReleaseCatalogClient.SyncResult(2, 1, 3));
        when(coordinator.createRollout(
                LocalClientVersionModels.RolloutScope.ALL_ONLINE,
                null,
                USER_ID,
                TRACE_ID))
                .thenReturn(new LocalClientUpdateCoordinator.RolloutResult(
                        new LocalClientVersionModels.Rollout(
                                "lcrl_api_test",
                                LocalClientVersionModels.RolloutScope.ALL_ONLINE,
                                null,
                                LocalClientVersionModels.RolloutStatus.RUNNING,
                                USER_ID,
                                Instant.parse("2026-08-20T10:00:00Z"),
                                null),
                        2));
        WebTestClient client = managementClient(catalog, policies, coordinator, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/local-opencode-client/version-management/releases/sync")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.synced").isEqualTo(2)
                .jsonPath("$.data.unchanged").isEqualTo(1);

        client.post()
                .uri("/api/internal/platform/local-opencode-client/version-management/rollouts")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"scope\":\"ALL_ONLINE\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.rolloutId").isEqualTo("lcrl_api_test")
                .jsonPath("$.data.attemptCount").isEqualTo(2);
    }

    @Test
    void versionManagementRejectsNonSuperAdminAtRequestTime() {
        WebTestClient client = managementClient(
                mock(LocalClientReleaseCatalogClient.class),
                mock(LocalClientVersionPolicyApplicationService.class),
                mock(LocalClientUpdateCoordinator.class),
                List.of());

        client.get()
                .uri("/api/internal/platform/local-opencode-client/version-management/releases")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void userUpdateOnlyUsesAuthenticatedUsersSingleInstanceAndNotification() {
        LocalClientUpdateCoordinator coordinator = mock(LocalClientUpdateCoordinator.class);
        LocalClientInstanceId instanceId = new LocalClientInstanceId("lci_local_version_api");
        UserNotificationId notificationId = new UserNotificationId("ntf_local_version_api");
        when(coordinator.createUserRequestedUpdate(
                USER_ID,
                instanceId,
                notificationId,
                "20260820190000",
                TRACE_ID))
                .thenReturn(new LocalClientUpdateCoordinator.RolloutResult(
                        new LocalClientVersionModels.Rollout(
                                "lcrl_user_api_test",
                                LocalClientVersionModels.RolloutScope.USER,
                                USER_ID,
                                LocalClientVersionModels.RolloutStatus.RUNNING,
                                USER_ID,
                                Instant.parse("2026-08-20T10:00:00Z"),
                                null),
                        1));
        WebTestClient client = userClient(coordinator);

        client.post()
                .uri("/api/internal/platform/local-opencode-client/instances/lci_local_version_api/updates")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"notificationId\":\"ntf_local_version_api\","
                        + "\"expectedTargetVersion\":\"20260820190000\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.attemptCount").isEqualTo(1);

        verify(coordinator).createUserRequestedUpdate(
                USER_ID, instanceId, notificationId, "20260820190000", TRACE_ID);
    }

    @Test
    void userUpdateReturnsForbiddenWhenCoordinatorRejectsOtherUsersInstance() {
        LocalClientUpdateCoordinator coordinator = mock(LocalClientUpdateCoordinator.class);
        when(coordinator.createUserRequestedUpdate(eq(USER_ID), any(), any(), any(), eq(TRACE_ID)))
                .thenThrow(new PlatformException(ErrorCode.FORBIDDEN, "本地客户端实例不属于当前用户"));
        WebTestClient client = userClient(coordinator);

        client.post()
                .uri("/api/internal/platform/local-opencode-client/instances/lci_other_users_device/updates")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"notificationId\":\"ntf_other_users_device\","
                        + "\"expectedTargetVersion\":\"20260820190000\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    private WebTestClient managementClient(
            LocalClientReleaseCatalogClient catalog,
            LocalClientVersionPolicyApplicationService policies,
            LocalClientUpdateCoordinator coordinator,
            List<String> roles) {
        return client(new LocalClientVersionManagementController(catalog, policies, coordinator), roles);
    }

    private WebTestClient userClient(LocalClientUpdateCoordinator coordinator) {
        return client(new LocalClientUpdateController(coordinator), List.of());
    }

    private WebTestClient client(Object controller, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "user",
                "AUTH-LOCAL-1",
                roles,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"));
        return WebTestClient.bindToController(controller)
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
