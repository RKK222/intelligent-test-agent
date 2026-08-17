package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.system.management.localclient.LocalClientRolloutApplicationService;
import com.enterprise.testagent.system.management.localclient.LocalClientRolloutResponses.RolloutUserView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class LocalClientRolloutAdminControllerTest {

    private static final UserId ADMIN = new UserId("usr_admin");
    private static final UserId TARGET = new UserId("usr_target");
    private static final Instant NOW = Instant.parse("2026-08-17T11:34:14Z");

    @Test
    void superAdminCanListEnableAndDisableRolloutUsers() {
        LocalClientRolloutApplicationService service = org.mockito.Mockito.mock(LocalClientRolloutApplicationService.class);
        RolloutUserView rollout = new RolloutUserView(TARGET.value(), true, ADMIN.value(), NOW, NOW);
        when(service.list(eq(new PageRequest(1, 50))))
                .thenReturn(new PageResponse<>(List.of(rollout), 1, 50, 1));
        when(service.enable(TARGET, ADMIN)).thenReturn(rollout);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get().uri("/api/internal/platform/local-opencode-client/admin/rollout-users")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data.items[0].userId").isEqualTo(TARGET.value())
                .jsonPath("$.data.total").isEqualTo(1);

        client.post().uri("/api/internal/platform/local-opencode-client/admin/rollout-users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"usr_target\"}")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data.enabled").isEqualTo(true);

        client.delete().uri("/api/internal/platform/local-opencode-client/admin/rollout-users/usr_target")
                .exchange().expectStatus().isOk();
        verify(service).disable(TARGET, ADMIN);
    }

    @Test
    void nonSuperAdminCannotManageRolloutUsers() {
        LocalClientRolloutApplicationService service = org.mockito.Mockito.mock(LocalClientRolloutApplicationService.class);
        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).get()
                .uri("/api/internal/platform/local-opencode-client/admin/rollout-users")
                .exchange().expectStatus().isForbidden().expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    private static WebTestClient client(LocalClientRolloutApplicationService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", ADMIN, "admin", "AUTH_ADMIN", roles,
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new LocalClientRolloutAdminController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
