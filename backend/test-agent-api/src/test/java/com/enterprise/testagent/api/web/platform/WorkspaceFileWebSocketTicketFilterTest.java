package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class WorkspaceFileWebSocketTicketFilterTest {

    @Test
    void rejectsInvalidTicketBeforeUpgradeWithoutEchoingRequestDetails() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        Mockito.doThrow(new PlatformException(ErrorCode.UNAUTHENTICATED, "文件 WebSocket 未授权"))
                .when(ticketService).validate("invalid", "https://console.example");
        WorkspaceFileWebSocketTicketFilter filter = new WorkspaceFileWebSocketTicketFilter(
                ticketService, new ObjectMapper());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
                        "/api/internal/platform/workspace-management/file/ws?ticket=invalid")
                .header("Origin", "https://console.example")
                .header("Host", "10.8.0.22:8080")
                .build());
        AtomicBoolean chained = new AtomicBoolean();
        WebFilterChain chain = ignored -> {
            chained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(chained).isFalse();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("UNAUTHENTICATED", "文件 WebSocket 未授权")
                .doesNotContain("invalid", "10.8.0.22", "Host");
    }

    @Test
    void validTicketIsOnlyPrevalidatedAndPassedToUpgradeChain() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceFileWebSocketTicketFilter filter = new WorkspaceFileWebSocketTicketFilter(
                ticketService, new ObjectMapper());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
                        "/api/internal/platform/workspace-management/file/ws?ticket=wft_valid")
                .header("Origin", "https://console.example")
                .build());
        AtomicBoolean chained = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            chained.set(true);
            return Mono.empty();
        }).block();

        assertThat(chained).isTrue();
        verify(ticketService).validate("wft_valid", "https://console.example");
        verify(ticketService, never()).consume(Mockito.anyString(), Mockito.anyString());
    }
}
