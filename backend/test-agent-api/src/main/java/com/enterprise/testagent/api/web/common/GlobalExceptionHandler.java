package com.enterprise.testagent.api.web.common;

import com.enterprise.testagent.common.api.ApiErrorResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.observability.TraceIdSupport;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * 处理业务层主动抛出的平台异常，保留其稳定错误码和安全 details。
     */
    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiErrorResponse> handlePlatformException(
            PlatformException exception,
            ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);
        ApiErrorResponse response = ApiErrorResponse.from(exception, traceId);
        return ResponseEntity.status(exception.errorCode().httpStatus()).body(response);
    }

    /**
     * 处理参数绑定、校验和非法参数异常，统一映射为 VALIDATION_ERROR。
     * 优先使用异常自身的消息，仅在消息为空时回退到默认错误说明。
     */
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
        String message = extractValidationMessage(exception);
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                firstNonBlank(message, ErrorCode.VALIDATION_ERROR.defaultMessage()),
                traceId,
                Map.of());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.httpStatus()).body(response);
    }

    /**
     * 从各类验证异常中提取面向用户的安全错误信息。
     */
    private String extractValidationMessage(Exception exception) {
        String rawMessage = exception.getMessage();
        if (rawMessage != null && !rawMessage.isBlank()) {
            // 精简 Spring 框架的长堆栈信息，只返回用户关心的字段错误
            return rawMessage.lines()
                    .filter(line -> !line.startsWith("org.") && !line.startsWith("\tat "))
                    .findFirst()
                    .orElse(rawMessage.lines().findFirst().orElse(rawMessage));
        }
        return null;
    }

    /**
     * 返回第一个非空非空白字符串。
     */
    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback;
    }

    /**
     * 处理数据库数据完整性违规（如外键约束冲突、唯一键冲突），返回 CONFLICT 错误。
     * 不暴露内部表名和约束名，仅提示用户数据冲突。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);
        LOGGER.warn("Data integrity violation, traceId={}", traceId, exception);
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.CONFLICT,
                "数据冲突：当前操作因存在关联数据无法执行，请先清理关联记录后重试",
                traceId,
                Map.of());
        return ResponseEntity.status(ErrorCode.CONFLICT.httpStatus()).body(response);
    }

    /**
     * 处理未预期异常，只记录服务端日志并返回不泄露内部细节的 INTERNAL_ERROR。
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Throwable exception,
            ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);
        LOGGER.error("Unhandled backend exception, traceId={}", traceId, exception);
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR, traceId);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus()).body(response);
    }

    /**
     * 从 exchange attribute 或响应头恢复 traceId，兜底生成合法 traceId。
     */
    private String traceIdFrom(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String traceId && TraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        String responseTraceId = exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        return TraceIdSupport.resolve(responseTraceId);
    }
}
