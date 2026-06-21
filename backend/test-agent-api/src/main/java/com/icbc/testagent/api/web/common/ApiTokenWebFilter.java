package com.icbc.testagent.api.web.common;

import com.icbc.testagent.common.api.ApiErrorResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.observability.TraceIdSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * API token 鉴权占位。未配置 token 时放行，配置后要求 Bearer token，便于后续替换为正式鉴权。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiTokenWebFilter implements WebFilter {

    private final String apiToken;
    private final ObjectMapper objectMapper;

    /**
     * 使用配置中的 API token 创建过滤器，空 token 表示本地/测试环境放行。
     */
    @Autowired
    public ApiTokenWebFilter(@Value("${test-agent.security.api-token:}") String apiToken) {
        this(apiToken, new ObjectMapper());
    }

    /**
     * 创建可注入 ObjectMapper 的过滤器，便于单元测试校验错误响应。
     */
    ApiTokenWebFilter(String apiToken, ObjectMapper objectMapper) {
        this.apiToken = apiToken == null || apiToken.isBlank() ? null : apiToken;
        this.objectMapper = objectMapper;
    }

    /**
     * 对 /api/ 路径执行 Bearer token 校验，未配置 token 或非 API 路径直接放行。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().pathWithinApplication().value().startsWith("/api/")
                || apiToken == null) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (("Bearer " + apiToken).equals(authorization)) {
            return chain.filter(exchange);
        }
        return unauthorized(exchange);
    }

    /**
     * 写出统一 401 错误响应，并确保响应头包含 traceId。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        String traceId = traceId(exchange);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        try {
            ApiErrorResponse body = ApiErrorResponse.of(ErrorCode.UNAUTHENTICATED, traceId);
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
}
