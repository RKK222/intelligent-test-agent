package com.enterprise.testagent.common.api;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.Map;
import java.util.Objects;

/**
 * 平台统一错误响应体，只承载安全的错误码、提示、traceId 和结构化详情。
 */
public record ApiErrorResponse(
        boolean success,
        String code,
        String message,
        String traceId,
        Map<String, Object> details) {

    /**
     * 校验错误响应的不变量，并复制 details，避免异常 details 在响应创建后被修改。
     */
    public ApiErrorResponse {
        if (success) {
            throw new IllegalArgumentException("error response must keep success=false");
        }
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    /**
     * 使用错误码默认说明构造错误响应，不附加 details。
     */
    public static ApiErrorResponse of(ErrorCode errorCode, String traceId) {
        return of(errorCode, errorCode.defaultMessage(), traceId, Map.of());
    }

    /**
     * 使用错误码默认说明和结构化 details 构造错误响应。
     */
    public static ApiErrorResponse of(ErrorCode errorCode, String traceId, Map<String, Object> details) {
        return of(errorCode, errorCode.defaultMessage(), traceId, details);
    }

    /**
     * 使用显式消息构造错误响应，调用方必须保证消息可安全暴露给前端。
     */
    public static ApiErrorResponse of(
            ErrorCode errorCode,
            String message,
            String traceId,
            Map<String, Object> details) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new ApiErrorResponse(false, errorCode.name(), message, traceId, details);
    }

    /**
     * 从平台异常转换为统一错误响应，保留稳定错误码、消息和结构化 details。
     */
    public static ApiErrorResponse from(PlatformException exception, String traceId) {
        Objects.requireNonNull(exception, "exception must not be null");
        return of(exception.errorCode(), exception.getMessage(), traceId, exception.details());
    }
}
