package com.icbc.testagent.api.web.platform;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.workspace.AgentConfigApplicationService;
import com.icbc.testagent.workspace.AgentConfigResponses.AgentConfigStatusResponse;
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
        when(service.publicStatus(false)).thenReturn(new AgentConfigStatusResponse(
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
    void createProgressTicketReturnsWebSocketUrl() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        AgentConfigOperationTicketStore ticketStore = new AgentConfigOperationTicketStore(
                Clock.fixed(Instant.parse("2026-06-26T00:00:00Z"), ZoneOffset.UTC),
                () -> "agt_fixedticket");
        WebTestClient client = client(service, new AgentConfigOperationTicketService(ticketStore), List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/workspace-management/agent-config/operations/aco_12345678/tickets")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticket").isEqualTo("agt_fixedticket")
                .jsonPath("$.data.webSocketUrl")
                .isEqualTo("/api/internal/platform/workspace-management/agent-config/operations/aco_12345678/ws?ticket=agt_fixedticket");
    }

    private WebTestClient client(AgentConfigApplicationService service, List<String> roles) {
        return client(service, new AgentConfigOperationTicketService(new AgentConfigOperationTicketStore()), roles);
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
        return WebTestClient.bindToController(new AgentConfigController(service, ticketService))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
