package com.example.testagent.app.web;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.observability.TraceConstants;
import com.example.testagent.observability.TraceIdSupport;
import java.util.Map;
import org.springframework.web.server.ServerWebExchange;

/**
 * Runtime API 入口层共享工具，只处理 HTTP 边界转换，不承载业务规则。
 */
final class RuntimeApiSupport {

    private RuntimeApiSupport() {
    }

    static String traceId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String traceId && TraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        return TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
    }

    static PageRequest pageRequest(Integer page, Integer size) {
        try {
            return new PageRequest(page == null ? 1 : page, size == null ? 50 : size);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("page", page == null ? 1 : page, "size", size == null ? 50 : size),
                    exception);
        }
    }
}
