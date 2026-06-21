package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.icbc.testagent.opencode.runtime.terminal.TerminalApplicationService;
import com.icbc.testagent.opencode.runtime.terminal.TerminalTicketRequest;
import com.icbc.testagent.opencode.runtime.terminal.TerminalTicketResponse;
import com.icbc.testagent.domain.session.SessionId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class TerminalControllerTest {

    @Test
    void createTicketReturnsUnifiedResponse() {
        TerminalApplicationService service = org.mockito.Mockito.mock(TerminalApplicationService.class);
        when(service.createTicket(
                        eq(new SessionId("ses_1234567890abcdef")),
                        eq(new TerminalTicketRequest("wrk_1234567890abcdef", ".", null, 80, 24)),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new TerminalTicketResponse(
                        "pty_1234567890abcdef",
                        Instant.parse("2026-06-19T00:01:00Z"),
                        "/api/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef"));
        WebTestClient client = WebTestClient.bindToController(new TerminalController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/sessions/ses_1234567890abcdef/terminal/tickets")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"workspaceId":"wrk_1234567890abcdef","cwd":".","cols":80,"rows":24}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.ticket").isEqualTo("pty_1234567890abcdef")
                .jsonPath("$.data.webSocketUrl").isEqualTo("/api/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");
    }

    @Test
    void createTicketAlsoExposesInternalPlatformTerminalUrl() {
        TerminalApplicationService service = org.mockito.Mockito.mock(TerminalApplicationService.class);
        when(service.createTicket(
                        eq(new SessionId("ses_1234567890abcdef")),
                        eq(new TerminalTicketRequest("wrk_1234567890abcdef", ".", null, 80, 24)),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new TerminalTicketResponse(
                        "pty_1234567890abcdef",
                        Instant.parse("2026-06-19T00:01:00Z"),
                        "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef"));
        WebTestClient client = WebTestClient.bindToController(new TerminalController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/tickets")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"workspaceId":"wrk_1234567890abcdef","cwd":".","cols":80,"rows":24}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.ticket").isEqualTo("pty_1234567890abcdef")
                .jsonPath("$.data.webSocketUrl").isEqualTo("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");
    }
}
