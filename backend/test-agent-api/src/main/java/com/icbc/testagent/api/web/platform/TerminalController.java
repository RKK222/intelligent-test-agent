package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.opencode.runtime.terminal.TerminalApplicationService;
import com.icbc.testagent.opencode.runtime.terminal.TerminalTicketRequest;
import com.icbc.testagent.opencode.runtime.terminal.TerminalTicketResponse;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.session.SessionId;
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

    /**
     * 注入终端应用服务，HTTP 层只负责发放短期 WebSocket ticket。
     */
    public TerminalController(TerminalApplicationService terminalService) {
        this.terminalService = terminalService;
    }

    /**
     * 创建终端连接 ticket，允许空请求体以便前端使用默认 cwd、shell 和窗口尺寸。
     */
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

    /**
     * 将 ticket 创建的阻塞校验逻辑移出 WebFlux event-loop。
     */
    private <T> Mono<ApiResponse<T>> blockingResponse(ServerWebExchange exchange, Function<String, T> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 内部平台路径需要返回同前缀 WebSocket URL，避免前端再做路径重写。
     */
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
