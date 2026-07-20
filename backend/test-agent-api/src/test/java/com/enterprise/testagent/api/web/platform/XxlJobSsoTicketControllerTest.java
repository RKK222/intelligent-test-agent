package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.xxljob.XxlJobSsoTicketIssue;
import com.enterprise.testagent.xxljob.XxlJobSsoTicketService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class XxlJobSsoTicketControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");
    private static final String TRACE_ID = "trace_xxl_sso_123456";

    @Test
    void superAdminCanIssueOneTimeFormTicket() {
        XxlJobSsoTicketService service = org.mockito.Mockito.mock(XxlJobSsoTicketService.class);
        when(service.issue(argThat(principal -> principal.userId().value().equals("usr_xxl_admin"))))
                .thenReturn(new XxlJobSsoTicketIssue(
                        "abcdefghijklmnopqrstuvwxyzABCDEFGH123456789",
                        NOW.plusSeconds(60),
                        "/xxl-job-admin/platform-sso/login"));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).post()
                .uri("/api/internal/platform/xxl-job/sso-tickets")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticket").isEqualTo("abcdefghijklmnopqrstuvwxyzABCDEFGH123456789")
                .jsonPath("$.data.expiresAt").isEqualTo("2026-07-20T00:01:00Z")
                .jsonPath("$.data.formAction").isEqualTo("/xxl-job-admin/platform-sso/login");
    }

    @Test
    void nonSuperAdminAndAnonymousCannotIssueTicket() {
        XxlJobSsoTicketService service = org.mockito.Mockito.mock(XxlJobSsoTicketService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).post()
                .uri("/api/internal/platform/xxl-job/sso-tickets")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        WebTestClient.bindToController(new XxlJobSsoTicketController(service))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .post()
                .uri("/api/internal/platform/xxl-job/sso-tickets")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    private static WebTestClient client(XxlJobSsoTicketService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "platform-token",
                new UserId("usr_xxl_admin"),
                "平台管理员",
                "AUTH_XXL",
                roles,
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new XxlJobSsoTicketController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
