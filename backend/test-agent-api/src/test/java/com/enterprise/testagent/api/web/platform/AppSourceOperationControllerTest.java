package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class AppSourceOperationControllerTest {

    private static final UserId USER_ID = new UserId("usr_1");
    private static final Instant NOW = Instant.parse("2026-07-28T04:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void currentMemberCanReadOperationAndCreateANewConnectionTicket() {
        AppSourceApplicationService appSources = mock(AppSourceApplicationService.class);
        AppSourceOperationTicketService tickets = mock(AppSourceOperationTicketService.class);
        when(appSources.getOperation("aso_12345678", USER_ID, false)).thenReturn(operation());
        when(tickets.createTicket(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("aso_12345678"),
                org.mockito.ArgumentMatchers.eq(TRACE_ID)))
                .thenReturn(new AppSourceDtos.TicketResponse(
                        "ast_ticket", NOW.plusSeconds(60), "ws://server-a/ws?ticket=ast_ticket"));
        WebTestClient client = client(appSources, tickets);

        client.get()
                .uri("/api/internal/platform/workspace-management/app-source-operations/aso_12345678")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.operationId").isEqualTo("aso_12345678")
                .jsonPath("$.data.status").isEqualTo("RUNNING");

        client.post()
                .uri("/api/internal/platform/workspace-management/app-source-operations/aso_12345678/ticket")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticket").isEqualTo("ast_ticket")
                .jsonPath("$.data.webSocketUrl").isEqualTo("ws://server-a/ws?ticket=ast_ticket");

        verify(appSources).getOperation("aso_12345678", USER_ID, false);
    }

    private WebTestClient client(
            AppSourceApplicationService appSources,
            AppSourceOperationTicketService tickets) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "U001", "普通用户", List.of("USER"),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new AppSourceOperationController(appSources, tickets))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AppSourceApplicationService.OperationSnapshot operation() {
        return new AppSourceApplicationService.OperationSnapshot(
                "aso_12345678", "app_1", "repo_1", null, 1L,
                AppSourceOperationType.DOWNLOAD, AppSourceOperationStatus.RUNNING,
                AppSourcePurpose.TEAM, "main", "b".repeat(40), List.of(),
                NOW.plusSeconds(3600), "trace_operation", NOW, null, List.of(), List.of());
    }
}
