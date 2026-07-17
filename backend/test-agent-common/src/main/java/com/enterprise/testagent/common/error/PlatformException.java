package com.enterprise.testagent.common.error;

import java.util.Map;
import java.util.Objects;

/**
 * 平台基础异常，业务层抛出稳定错误码，入口层统一转换为 ApiErrorResponse。
 */
public class PlatformException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    /**
     * 使用错误码默认说明构造平台异常。
     */
    public PlatformException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Map.of(), null);
    }

    /**
     * 使用显式安全消息构造平台异常。
     */
    public PlatformException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of(), null);
    }

    /**
     * 使用显式安全消息和结构化 details 构造平台异常。
     */
    public PlatformException(ErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode, message, details, null);
    }

    /**
     * 使用显式安全消息、结构化 details 和原始原因构造平台异常。
     */
    public PlatformException(ErrorCode errorCode, String message, Map<String, Object> details, Throwable cause) {
        super(resolveMessage(errorCode, message), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    /**
     * 返回稳定错误码，入口层据此映射 HTTP 状态和错误响应 code。
     */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * 返回可安全序列化的结构化错误详情。
     */
    public Map<String, Object> details() {
        return details;
    }

    /**
     * 空消息时回退到错误码默认中文说明，避免向前端返回空错误信息。
     */
    private static String resolveMessage(ErrorCode errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (message == null || message.isBlank()) {
            return errorCode.defaultMessage();
        }
        return message;
    }
}
