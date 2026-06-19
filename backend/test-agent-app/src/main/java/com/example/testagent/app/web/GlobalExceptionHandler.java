package com.example.testagent.app.web;

import com.example.testagent.common.api.ApiErrorResponse;
import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.observability.TraceConstants;
import com.example.testagent.observability.TraceIdSupport;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/**
 * 全局异常处理器，统一把入口层异常转换为平台错误响应，避免内部异常直接暴露给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiErrorResponse> handlePlatformException(
            PlatformException exception,
            ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);
        ApiErrorResponse response = ApiErrorResponse.from(exception, traceId);
        return ResponseEntity.status(exception.errorCode().httpStatus()).body(response);
    }

    @ExceptionHandler({
            ServerWebInputException.class,
            WebExchangeBindException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            Exception exception,
            ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                traceId,
                Map.of("exception", exception.getClass().getSimpleName()));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.httpStatus()).body(response);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Throwable exception,
            ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);
        LOGGER.error("Unhandled backend exception, traceId={}", traceId, exception);
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR, traceId);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus()).body(response);
    }

    private String traceIdFrom(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String traceId && TraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        String responseTraceId = exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        return TraceIdSupport.resolve(responseTraceId);
    }
}
