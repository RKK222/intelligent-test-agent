package com.example.testagent.app.web;

import com.example.testagent.app.terminal.TerminalApplicationService;
import com.example.testagent.app.terminal.TerminalTicketRequest;
import com.example.testagent.app.terminal.TerminalTicketResponse;
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

    @PostMapping("/api/sessions/{sessionId}/terminal/tickets")
    public Mono<ApiResponse<TerminalTicketResponse>> createTicket(
            @PathVariable String sessionId,
            @RequestBody(required = false) TerminalTicketRequest request,
            ServerWebExchange exchange) {
        TerminalTicketRequest resolved = request == null ? new TerminalTicketRequest(null, null, null, null, null) : request;
        return blockingResponse(exchange, traceId -> terminalService.createTicket(new SessionId(sessionId), resolved, traceId));
    }

    private <T> Mono<ApiResponse<T>> blockingResponse(ServerWebExchange exchange, Function<String, T> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
