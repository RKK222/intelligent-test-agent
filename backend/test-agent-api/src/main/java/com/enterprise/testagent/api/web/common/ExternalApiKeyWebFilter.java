package com.enterprise.testagent.api.web.common;

import com.enterprise.testagent.common.api.ApiErrorResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.externalapi.ExternalApiPrincipal;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.observability.TraceIdSupport;
import com.enterprise.testagent.system.management.externalapi.ExternalApiCredentialRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** 外部 API 专用 Header 认证；用户 Token 和静态 Token 均不能替代。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class ExternalApiKeyWebFilter implements WebFilter {

    public static final String TOOL_CODE_HEADER = "X-Test-Agent-Tool-Code";
    public static final String API_KEY_HEADER = "X-Test-Agent-Api-Key";
    private final ExternalApiCredentialRegistry registry;
    private final ObjectMapper objectMapper;

    public ExternalApiKeyWebFilter(ExternalApiCredentialRegistry registry, ObjectMapper objectMapper) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!ExternalApiWebSupport.isExternalPath(path)) {
            return chain.filter(exchange);
        }
        String toolCode = exchange.getRequest().getHeaders().getFirst(TOOL_CODE_HEADER);
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (toolCode == null || toolCode.isBlank() || apiKey == null || apiKey.isBlank()) {
            return writeError(exchange, ErrorCode.UNAUTHENTICATED);
        }
        try {
            ExternalApiPrincipal principal = registry.authenticate(toolCode, apiKey);
            exchange.getAttributes().put(ExternalApiWebSupport.PRINCIPAL_ATTR, principal);
            return chain.filter(exchange);
        } catch (PlatformException exception) {
            ErrorCode code = exception.errorCode() == ErrorCode.EXTERNAL_API_UNAVAILABLE
                    ? ErrorCode.EXTERNAL_API_UNAVAILABLE : ErrorCode.UNAUTHENTICATED;
            return writeError(exchange, code);
        } catch (RuntimeException exception) {
            return writeError(exchange, ErrorCode.EXTERNAL_API_UNAVAILABLE);
        }
    }

    private Mono<Void> writeError(ServerWebExchange exchange, ErrorCode code) {
        String traceId = traceId(exchange);
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(code.httpStatus()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        try {
            byte[] bytes = objectMapper.writeValueAsString(ApiErrorResponse.of(code, traceId))
                    .getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception exception) {
            return exchange.getResponse().setComplete();
        }
    }

    private static String traceId(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE);
        if (value instanceof String traceId && TraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        return TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
    }
}
