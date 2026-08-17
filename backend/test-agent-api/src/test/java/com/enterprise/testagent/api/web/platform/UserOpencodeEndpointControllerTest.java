package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.enterprise.testagent.system.management.localclient.LocalClientInstanceApplicationService;
import com.enterprise.testagent.system.management.localclient.LocalClientRolloutApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class UserOpencodeEndpointControllerTest {

    private static final UserId USER_ID = new UserId("usr_target");
    private static final Instant NOW = Instant.parse("2026-08-17T11:34:14Z");

    @Test
    void serverCapabilityReflectsRolloutMembershipAndDefaultsToHidden() {
        UserOpencodeProcessAssignmentService process = org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        LocalClientInstanceApplicationService instances = org.mockito.Mockito.mock(LocalClientInstanceApplicationService.class);
        LocalClientRolloutApplicationService rollout = org.mockito.Mockito.mock(LocalClientRolloutApplicationService.class);
        when(process.status(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.eq("opencode"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new UserOpencodeProcessStatusResponse(
                        UserOpencodeProcessAvailability.READY, false, "ready", "opc_1", "linux-1", "container-1",
                        4098, "http://127.0.0.1:4098", NOW));
        when(instances.list(USER_ID)).thenReturn(List.of());
        when(rollout.isDownloadAllowed(USER_ID)).thenReturn(false, true);
        WebTestClient client = client(process, instances, rollout);

        client.get().uri("/api/internal/agent/opencode/opencode-endpoints/me")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data[0].capabilities.localClientDownload").isEqualTo(false);
        client.get().uri("/api/internal/agent/opencode/opencode-endpoints/me")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data[0].capabilities.localClientDownload").isEqualTo(true);
    }

    @Test
    void rolloutLookupFailureFailsClosedWithoutBreakingEndpointList() {
        UserOpencodeProcessAssignmentService process = org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        LocalClientInstanceApplicationService instances = org.mockito.Mockito.mock(LocalClientInstanceApplicationService.class);
        LocalClientRolloutApplicationService rollout = org.mockito.Mockito.mock(LocalClientRolloutApplicationService.class);
        when(process.status(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.eq("opencode"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new UserOpencodeProcessStatusResponse(
                        UserOpencodeProcessAvailability.READY, false, "ready", "opc_1", "linux-1", "container-1",
                        4098, "http://127.0.0.1:4098", NOW));
        when(instances.list(USER_ID)).thenReturn(List.of());
        when(rollout.isDownloadAllowed(USER_ID)).thenThrow(new IllegalStateException("database unavailable"));

        client(process, instances, rollout).get()
                .uri("/api/internal/agent/opencode/opencode-endpoints/me")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data[0].capabilities.localClientDownload").isEqualTo(false);
    }

    private static WebTestClient client(
            UserOpencodeProcessAssignmentService process,
            LocalClientInstanceApplicationService instances,
            LocalClientRolloutApplicationService rollout) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "target", "AUTH_TARGET", List.of("USER"),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new UserOpencodeEndpointController(process, instances, rollout))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
