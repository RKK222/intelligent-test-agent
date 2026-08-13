package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.api.ApiErrorResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.observability.TraceIdSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebSocket upgrade 前非消费式校验一次性 ticket，避免畸形 Upgrade 先触发框架错误并泄露请求信息。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WorkspaceFileWebSocketTicketFilter implements WebFilter {

    private static final String FILE_WS_PATH = "/api/internal/platform/workspace-management/file/ws";

    private final WorkspaceFileSocketTicketService ticketService;
    private final ObjectMapper objectMapper;

    public WorkspaceFileWebSocketTicketFilter(
            WorkspaceFileSocketTicketService ticketService,
            ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!FILE_WS_PATH.equals(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        try {
            ticketService.validate(
                    exchange.getRequest().getQueryParams().getFirst("ticket"),
                    exchange.getRequest().getHeaders().getOrigin());
            return chain.filter(exchange);
        } catch (PlatformException exception) {
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        String traceId = TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(ApiErrorResponse.of(
                    ErrorCode.UNAUTHENTICATED, "文件 WebSocket 未授权", traceId, Map.of()));
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception ignored) {
            return exchange.getResponse().setComplete();
        }
    }
}
