package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiErrorResponse;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.observability.TraceConstants;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 跨 Java 路由过滤器的统一错误响应写入器。
 *
 * <p>WebFilter 中发生的异常不会进入 ControllerAdvice，因此写请求的路由失败必须在过滤器层
 * 转换为平台标准响应，且不能继续执行当前 Java 的业务 Controller。
 */
@Component
class BackendRoutingErrorWriter {

    private final ObjectMapper objectMapper;

    BackendRoutingErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 写出平台异常的稳定状态码、traceId 和安全 details；响应已提交时保留原异常。
     */
    Mono<Void> writePlatformError(ServerWebExchange exchange, PlatformException exception) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(exception, "exception must not be null");
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }
        String traceId = RuntimeApiSupport.traceId(exchange);
        try {
            byte[] body = objectMapper.writeValueAsBytes(ApiErrorResponse.from(exception, traceId));
            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(exception.errorCode().httpStatus()));
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception serializationFailure) {
            return Mono.error(serializationFailure);
        }
    }
}
