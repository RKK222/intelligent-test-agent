package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.opencode.runtime.terminal.TerminalApplicationService;
import com.enterprise.testagent.opencode.runtime.terminal.TerminalTicketRequest;
import com.enterprise.testagent.opencode.runtime.terminal.TerminalTicketResponse;
import com.enterprise.testagent.opencode.runtime.terminal.ServerTerminalTicketRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

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
                        "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef"));
        WebTestClient client = WebTestClient.bindToController(new TerminalController(service, webSocketUrlFactory()))
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
                .jsonPath("$.data.webSocketUrl").isEqualTo("ws://122.233.30.114:8080/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");
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
        WebTestClient client = WebTestClient.bindToController(new TerminalController(service, webSocketUrlFactory()))
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
                .jsonPath("$.data.webSocketUrl").isEqualTo("ws://122.233.30.114:8080/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");
    }

    @Test
    void createsSuperAdminServerTicketWithWssGatewayUrl() {
        TerminalApplicationService service = org.mockito.Mockito.mock(TerminalApplicationService.class);
        RuntimeManagementBackendRoutingService routing = org.mockito.Mockito.mock(RuntimeManagementBackendRoutingService.class);
        ServerTerminalTicketRequest request = new ServerTerminalTicketRequest("ROOT@server-a", 120, 32);
        when(routing.forwardTargetForLinuxServer(any(), eq(new LinuxServerId("server-a"))))
                .thenReturn(Optional.empty());
        when(service.createServerTicket(
                eq(new LinuxServerId("server-a")), eq(new UserId("usr_admin")), eq(request), eq("trace_1234567890abcdef")))
                .thenReturn(new TerminalTicketResponse(
                        "pty_root", Instant.parse("2026-07-18T06:00:00Z"), "/unused"));
        CurrentBackendWebSocketUrlFactory urls = new CurrentBackendWebSocketUrlFactory(
                org.mockito.Mockito.mock(BackendInstanceIdentity.class), "wss://console.example");
        TerminalController controller = new TerminalController(service, urls, routing);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/platform/opencode-runtime/management/linux-servers/server-a/terminal/tickets")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .build());
        exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                "token", new UserId("usr_admin"), "admin", "AUTH_ADMIN",
                List.of(Dictionary.ROLE_SUPER_ADMIN), Instant.now(), Instant.now().plusSeconds(3600)));

        var response = controller.createServerTicket("server-a", request, exchange).block();

        org.assertj.core.api.Assertions.assertThat(response).isNotNull();
        org.assertj.core.api.Assertions.assertThat(response.data().webSocketUrl())
                .isEqualTo("wss://console.example/api/internal/platform/opencode-runtime/management/linux-servers/server-a/terminal/ws?ticket=pty_root");
    }

    private CurrentBackendWebSocketUrlFactory webSocketUrlFactory() {
        BackendInstanceIdentity identity = org.mockito.Mockito.mock(BackendInstanceIdentity.class);
        when(identity.listenUrl()).thenReturn("http://122.233.30.114:8080");
        return new CurrentBackendWebSocketUrlFactory(identity);
    }
}
