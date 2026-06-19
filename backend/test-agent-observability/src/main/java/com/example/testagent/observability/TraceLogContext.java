package com.example.testagent.observability;

import org.slf4j.MDC;

/**
 * 日志上下文工具，统一把入口 traceId 写入 SLF4J MDC，供 Log4j2 PatternLayout 输出结构化字段。
 */
public final class TraceLogContext {

    public static final String TRACE_ID_MDC_KEY = TraceConstants.TRACE_ID_CONTEXT_KEY;

    private TraceLogContext() {
    }

    public static String currentTraceId() {
        return MDC.get(TRACE_ID_MDC_KEY);
    }

    public static void putTraceId(String traceId) {
        if (TraceIdSupport.isValid(traceId)) {
            MDC.put(TRACE_ID_MDC_KEY, traceId.trim());
            return;
        }
        clearTraceId();
    }

    public static void clearTraceId() {
        MDC.remove(TRACE_ID_MDC_KEY);
    }

    public static Scope withTraceId(String traceId) {
        String previousTraceId = currentTraceId();
        putTraceId(traceId);
        return () -> restoreTraceId(previousTraceId);
    }

    private static void restoreTraceId(String previousTraceId) {
        if (previousTraceId == null) {
            clearTraceId();
            return;
        }
        MDC.put(TRACE_ID_MDC_KEY, previousTraceId);
    }

    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
