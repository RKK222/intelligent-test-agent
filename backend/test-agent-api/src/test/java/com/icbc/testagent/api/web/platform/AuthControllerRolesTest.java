package com.icbc.testagent.api.web.platform;

import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.system.management.auth.AuthApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class AuthControllerRolesTest {

    @Test
    void meReturnsRolesFromAuthenticatedPrincipal() {
        AuthApplicationService service = org.mockito.Mockito.mock(AuthApplicationService.class);
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_1234567890abcdef"),
                "admin",
                "AUTH_1",
                List.of(Dictionary.ROLE_APP_ADMIN),
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        WebTestClient client = WebTestClient.bindToController(new AuthController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .build();

        client.get()
                .uri("/api/auth/me")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.roles[0]").isEqualTo("APP_ADMIN");
    }

    @Test
    void loginReturnsRolesLoadedByAuthService() {
        AuthApplicationService service = org.mockito.Mockito.mock(AuthApplicationService.class);
        when(service.login("admin", "secret", "unknown", null))
                .thenReturn(new AuthPrincipal(
                        "token",
                        new UserId("usr_1234567890abcdef"),
                        "admin",
                        "AUTH_1",
                        List.of(Dictionary.ROLE_APP_ADMIN),
                        Instant.parse("2026-06-23T00:00:00Z"),
                        Instant.parse("2026-06-24T00:00:00Z")));
        WebTestClient client = WebTestClient.bindToController(new AuthController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/auth/login")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"admin","password":"secret"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.roles[0]").isEqualTo("APP_ADMIN");
    }
}
