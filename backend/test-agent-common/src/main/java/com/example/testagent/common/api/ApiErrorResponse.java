package com.example.testagent.common.api;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
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

    public static ApiErrorResponse of(ErrorCode errorCode, String traceId) {
        return of(errorCode, errorCode.defaultMessage(), traceId, Map.of());
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String traceId, Map<String, Object> details) {
        return of(errorCode, errorCode.defaultMessage(), traceId, details);
    }

    public static ApiErrorResponse of(
            ErrorCode errorCode,
            String message,
            String traceId,
            Map<String, Object> details) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new ApiErrorResponse(false, errorCode.name(), message, traceId, details);
    }

    public static ApiErrorResponse from(PlatformException exception, String traceId) {
        Objects.requireNonNull(exception, "exception must not be null");
        return of(exception.errorCode(), exception.getMessage(), traceId, exception.details());
    }
}
