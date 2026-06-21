package com.example.testagent.api.web.common;

import com.example.testagent.observability.TraceConstants;
import com.example.testagent.observability.TraceIdSupport;
import com.example.testagent.observability.TraceLogContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux 入口 traceId 过滤器，负责请求透传/生成、exchange attribute 和响应头写入。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    /**
     * 为每个请求解析或生成 traceId，并写入 exchange、响应头、日志 MDC 和 Reactor context。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, traceId);
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        return Mono.defer(() -> {
                    TraceLogContext.Scope scope = TraceLogContext.withTraceId(traceId);
                    try {
                        return chain.filter(exchange)
                                .doFinally(ignored -> scope.close());
                    } catch (RuntimeException exception) {
                        scope.close();
                        return Mono.error(exception);
                    }
                })
                .contextWrite(context -> context.put(TraceConstants.TRACE_ID_CONTEXT_KEY, traceId));
    }
}
