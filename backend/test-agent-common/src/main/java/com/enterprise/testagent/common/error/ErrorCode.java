package com.enterprise.testagent.common.error;

/**
 * 对外稳定错误码。HTTP 状态仅作为入口层映射依据，不把 Spring Web 类型带入 common。
 */
public enum ErrorCode {
    VALIDATION_ERROR(400, "请求参数无效"),
    UNAUTHENTICATED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "状态冲突"),
    CONVERSATION_CONTEXT_REQUIRED(409, "需要会话运行上下文"),
    CONVERSATION_CONTEXT_EXPIRED(409, "会话运行上下文已过期"),
    API_GONE(410, "接口已作废"),
    RUN_DETAILS_EXPIRED(410, "运行详情已过期"),
    RATE_LIMITED(429, "请求过于频繁"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    OPENCODE_BAD_GATEWAY(502, "TestAgent 服务响应异常"),
    OPENCODE_UNAVAILABLE(503, "TestAgent 服务不可用"),
    TERMINAL_UNAVAILABLE(503, "终端服务不可用"),
    OPENCODE_TIMEOUT(504, "TestAgent 服务超时"),
    RUNTIME_STATE_UNAVAILABLE(503, "运行态存储不可用"),
    GIT_UNAVAILABLE(503, "Git 服务不可用"),
    GIT_TIMEOUT(504, "Git 操作超时");

    private final int httpStatus;
    private final String defaultMessage;

    /**
     * 绑定稳定错误码到 HTTP 状态和安全默认说明；这里不引入 Spring 类型，保持 common 轻量。
     */
    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 返回入口层应映射的 HTTP 状态码，供统一异常处理和 WebFilter 直接写出错误响应。
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * 返回可直接暴露给前端的默认中文错误说明，不包含内部堆栈或第三方敏感信息。
     */
    public String defaultMessage() {
        return defaultMessage;
    }
}
