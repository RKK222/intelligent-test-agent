package com.example.testagent.api.web.common;

import com.example.testagent.common.api.ApiErrorResponse;
import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.observability.TraceConstants;
import com.example.testagent.observability.TraceIdSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 内存限流占位实现，用于本地和测试验证入口边界；生产可替换为 Redis 或网关限流。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class InMemoryRateLimitWebFilter implements WebFilter {

    private final boolean enabled;
    private final int capacity;
    private final Duration window;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * 使用配置创建内存固定窗口限流器，非法容量或窗口会归一为安全默认值。
     */
    @Autowired
    public InMemoryRateLimitWebFilter(
            @Value("${test-agent.rate-limit.enabled:false}") boolean enabled,
            @Value("${test-agent.rate-limit.capacity:120}") int capacity,
            @Value("${test-agent.rate-limit.window:1m}") Duration window) {
        this.enabled = enabled;
        this.capacity = Math.max(1, capacity);
        this.window = window == null || window.isZero() || window.isNegative() ? Duration.ofMinutes(1) : window;
    }

    /**
     * 对 /api/ 请求执行内存限流；关闭限流或非 API 路径直接放行。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled || !exchange.getRequest().getPath().pathWithinApplication().value().startsWith("/api/")) {
            return chain.filter(exchange);
        }
        String key = rateLimitKey(exchange);
        Instant now = Instant.now();
        Bucket bucket = buckets.compute(key, (ignored, existing) -> {
            if (existing == null || now.isAfter(existing.windowStartedAt().plus(window))) {
                return new Bucket(now, 1);
            }
            return new Bucket(existing.windowStartedAt(), existing.used() + 1);
        });
        if (bucket.used() > capacity) {
            return rateLimited(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 计算限流 key，优先使用 X-Forwarded-For 的首个地址，否则使用远端 IP。
     */
    private String rateLimitKey(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    /**
     * 写出统一 429 错误响应，并保持 traceId 响应头。
     */
    private Mono<Void> rateLimited(ServerWebExchange exchange) {
        String traceId = traceId(exchange);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        try {
            ApiErrorResponse body = ApiErrorResponse.of(ErrorCode.RATE_LIMITED, traceId);
            byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception exception) {
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * 从 exchange 或请求头恢复 traceId，缺失或非法时生成新 traceId。
     */
    private String traceId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String traceId && TraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        return TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
    }

    private record Bucket(Instant windowStartedAt, int used) {
    }
}
