package com.example.testagent.common.error;

/**
 * 对外稳定错误码。HTTP 状态仅作为入口层映射依据，不把 Spring Web 类型带入 common。
 */
public enum ErrorCode {
    VALIDATION_ERROR(400, "请求参数无效"),
    UNAUTHENTICATED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "状态冲突"),
    RATE_LIMITED(429, "请求过于频繁"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    OPENCODE_BAD_GATEWAY(502, "opencode 服务响应异常"),
    OPENCODE_UNAVAILABLE(503, "opencode 服务不可用"),
    OPENCODE_TIMEOUT(504, "opencode 服务超时");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
