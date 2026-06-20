package com.example.testagent.observability;

/**
 * TraceId 字段常量，统一 HTTP 头、WebExchange attribute 和 Reactor context 的键名。
 */
public final class TraceConstants {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_ATTRIBUTE = "testAgent.traceId";
    public static final String TRACE_ID_CONTEXT_KEY = "traceId";

    /**
     * 常量类不允许实例化，所有调用方直接引用公开常量。
     */
    private TraceConstants() {
    }
}
