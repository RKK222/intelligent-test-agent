package com.example.testagent.app.web;

import com.example.testagent.observability.TraceConstants;
import com.example.testagent.observability.TraceIdSupport;
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, traceId);
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        return chain.filter(exchange)
                .contextWrite(context -> context.put(TraceConstants.TRACE_ID_CONTEXT_KEY, traceId));
    }
}
