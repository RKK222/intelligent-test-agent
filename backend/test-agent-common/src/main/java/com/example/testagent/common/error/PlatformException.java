package com.example.testagent.common.error;

import java.util.Map;
import java.util.Objects;

/**
 * 平台基础异常，业务层抛出稳定错误码，入口层统一转换为 ApiErrorResponse。
 */
public class PlatformException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public PlatformException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Map.of(), null);
    }

    public PlatformException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of(), null);
    }

    public PlatformException(ErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode, message, details, null);
    }

    public PlatformException(ErrorCode errorCode, String message, Map<String, Object> details, Throwable cause) {
        super(resolveMessage(errorCode, message), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }

    private static String resolveMessage(ErrorCode errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (message == null || message.isBlank()) {
            return errorCode.defaultMessage();
        }
        return message;
    }
}
