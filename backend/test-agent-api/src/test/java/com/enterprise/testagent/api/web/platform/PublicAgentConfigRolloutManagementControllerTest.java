package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutCoordinator;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class PublicAgentConfigRolloutManagementControllerTest {

    @Test
    void superAdminCanDecommissionOfflineRolloutMember() {
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);
        WebTestClient client = client(coordinator, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/opencode-runtime/management/linux-servers/linux-old/decommission")
                .header("X-Trace-Id", "trace-decommission")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.linuxServerId").isEqualTo("linux-old")
                .jsonPath("$.data.membershipStatus").isEqualTo("DECOMMISSIONED");

        verify(coordinator).decommissionServer("linux-old");
    }

    @Test
    void ordinaryUserCannotDecommissionRolloutMember() {
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);

        client(coordinator, List.of(Dictionary.ROLE_USER)).post()
                .uri("/api/internal/platform/opencode-runtime/management/linux-servers/linux-old/decommission")
                .exchange()
                .expectStatus().isForbidden();
    }

    private static WebTestClient client(PublicAgentConfigRolloutCoordinator coordinator, List<String> roles) {
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr-admin"),
                "admin",
                "AUTH-1",
                roles,
                now,
                now.plusSeconds(3600));
        return WebTestClient.bindToController(new PublicAgentConfigRolloutManagementController(coordinator))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
