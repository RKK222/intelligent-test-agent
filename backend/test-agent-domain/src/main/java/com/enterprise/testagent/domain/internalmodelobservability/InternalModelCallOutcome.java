package com.enterprise.testagent.domain.internalmodelobservability;

/**
 * 内部模型代理调用结果分类。只分类、不携带任何请求/响应正文，满足安全脱敏边界：
 * 仅 traceId、耗时、状态与稳定错误信息参与观测。
 */
public enum InternalModelCallOutcome {
    /** 上游 2xx 且流正常结束。 */
    SUCCESS,
    /** 代理 apiKey 鉴权失败。 */
    PROXY_AUTH_FAILED,
    /** provider 不存在、未启用或 Token 未配置。 */
    PROVIDER_UNAVAILABLE,
    /** 请求体校验失败：缺 model、非法 JSON、超 2 MiB 上限。 */
    REQUEST_INVALID,
    /** 上游连接失败：DNS、拒绝连接或连接超时。 */
    UPSTREAM_CONNECT_FAILED,
    /** 30 秒内未收到上游响应头。 */
    UPSTREAM_FIRST_RESPONSE_TIMEOUT,
    /** SSE 首事件 30 秒超时。 */
    UPSTREAM_FIRST_EVENT_TIMEOUT,
    /** 相邻 SSE 事件空闲超过 120 秒。 */
    UPSTREAM_STREAM_IDLE_TIMEOUT,
    /** 上游返回非 2xx 状态。 */
    UPSTREAM_HTTP_ERROR,
    /** responses 适配：上游流在完成前正常结束。 */
    UPSTREAM_STREAM_INTERRUPTED,
    /** responses 适配：上游流读取异常。 */
    UPSTREAM_STREAM_FAILED,
    /** 下游 opencode 提前断开（取消信号）。 */
    CLIENT_DISCONNECTED,
    /** 未归类的其他异常。 */
    UNKNOWN_ERROR
}
