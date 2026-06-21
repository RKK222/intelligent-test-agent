package com.icbc.testagent.api.web.common;

import com.icbc.testagent.common.api.ApiErrorResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.auth.TokenStore;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.observability.TraceIdSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
 * 用户 Token 鉴权过滤器。在 ApiTokenWebFilter 之前执行，优先判断用户 Bearer Token。
 * - 登录接口 /api/auth/login 不需要 Token，直接放行。
 * - 存在有效 Bearer Token 时设置认证属性，供 Controller 使用。
 * - Token 无效（过期或格式错误）返回 401。
 * - 无 Token 时直接放行，由后续 ApiTokenWebFilter 或 Controller 判断是否有必要鉴权。
 *
 * 与 ApiTokenWebFilter 的关系：
 * - JwtAuthWebFilter（Order +10）处理用户 Token，设置 AuthPrincipal。
 * - ApiTokenWebFilter（Order +20）作为静态 API Token 兜底。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthWebFilter implements WebFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    /**
     * 使用 Token 存储和 JSON 序列化器创建过滤器。
     */
    public JwtAuthWebFilter(TokenStore tokenStore, ObjectMapper objectMapper) {
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 对 /api/ 路径执行用户 Token 校验，跳过登录接口。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String token = AuthWebSupport.extractBearerToken(exchange);

        // 非 API 路径或登录接口直接放行
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        if (path.equals(LOGIN_PATH) || token == null) {
            return chain.filter(exchange);
        }

        // 有 Token 时验证其有效性
        Optional<AuthPrincipal> found = tokenStore.findByToken(token);
        if (found.isEmpty()) {
            // Token 在存储中不存在：可能是旧版静态 API Token，放行给后续过滤器处理
            return chain.filter(exchange);
        }
        AuthPrincipal principal = found.get();
        if (principal.isExpired()) {
            tokenStore.delete(token);
            return unauthorized(exchange);
        }
        exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
        return chain.filter(exchange);
    }

    /**
     * 写出统一 401 错误响应，并确保响应头包含 traceId。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        String traceId = resolveTraceId(exchange);
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
     * 从 exchange attribute 或请求头恢复 traceId。
     */
    private String resolveTraceId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String traceId && TraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        return TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
    }
}
