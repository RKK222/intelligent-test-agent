package com.example.testagent.api.web;

import com.example.testagent.opencode.runtime.terminal.TerminalApplicationService;
import com.example.testagent.opencode.runtime.terminal.TerminalTicketRequest;
import com.example.testagent.opencode.runtime.terminal.TerminalTicketResponse;
import com.example.testagent.common.api.ApiResponse;
import com.example.testagent.domain.session.SessionId;
import java.util.function.Function;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * PTY terminal HTTP 入口。WebSocket upgrade 由 TerminalWebSocketHandler 处理。
 */
@RestController
public class TerminalController {

    private final TerminalApplicationService terminalService;

    public TerminalController(TerminalApplicationService terminalService) {
        this.terminalService = terminalService;
    }

    @PostMapping({
            "/api/sessions/{sessionId}/terminal/tickets",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/tickets"
    })
    public Mono<ApiResponse<TerminalTicketResponse>> createTicket(
            @PathVariable String sessionId,
            @RequestBody(required = false) TerminalTicketRequest request,
            ServerWebExchange exchange) {
        TerminalTicketRequest resolved = request == null ? new TerminalTicketRequest(null, null, null, null, null) : request;
        return blockingResponse(exchange, traceId -> terminalTicketResponse(
                sessionId,
                terminalService.createTicket(new SessionId(sessionId), resolved, traceId),
                exchange));
    }

    private <T> Mono<ApiResponse<T>> blockingResponse(ServerWebExchange exchange, Function<String, T> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private TerminalTicketResponse terminalTicketResponse(
            String sessionId,
            TerminalTicketResponse response,
            ServerWebExchange exchange) {
        String requestPath = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!requestPath.startsWith("/api/internal/platform/opencode-runtime/")) {
            return response;
        }
        return new TerminalTicketResponse(
                response.ticket(),
                response.expiresAt(),
                "/api/internal/platform/opencode-runtime/sessions/" + sessionId + "/terminal/ws?ticket=" + response.ticket());
    }
}
