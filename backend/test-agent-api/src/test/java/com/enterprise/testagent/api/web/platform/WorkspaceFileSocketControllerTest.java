package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 回归文件 ticket 的阻塞式数据库与 Redis 校验不能占用 reactor-http event-loop。 */
class WorkspaceFileSocketControllerTest {

    @Test
    void createTicketRunsBlockingRouteChecksOnBoundedElastic() {
        WorkspaceFileSocketTicketService tickets = mock(WorkspaceFileSocketTicketService.class);
        when(tickets.createTicket(
                any(AuthPrincipal.class),
                any(WorkspaceFileSocketDtos.TicketRequest.class),
                eq(null),
                eq("trace_file_ticket"))).thenAnswer(ignored -> {
                    assertThat(Thread.currentThread().getName()).contains("boundedElastic");
                    return new WorkspaceFileSocketDtos.TicketResponse(
                            "ticket-local",
                            Instant.parse("2026-08-22T10:00:00Z"),
                            "/api/internal/platform/workspace-management/file/ws");
                });
        WebTestClient client = WebTestClient.bindToController(new WorkspaceFileSocketController(
                        mock(WorkspaceFileRoutingService.class), tickets))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                            "token", new UserId("usr_file_ticket"), "user", "888888888", List.of("USER"),
                            Instant.now(), Instant.now().plusSeconds(3600)));
                    return chain.filter(exchange);
                })
                .build();

        client.post()
                .uri("/api/internal/platform/workspace-management/file-ws/tickets")
                .header("X-Trace-Id", "trace_file_ticket")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"workspaceId":"wrk_local","mode":"workspace"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticket").isEqualTo("ticket-local");
    }
}
