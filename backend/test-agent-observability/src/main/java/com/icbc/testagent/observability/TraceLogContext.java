package com.icbc.testagent.observability;

import org.slf4j.MDC;

/**
 * 日志上下文工具，统一把入口 traceId 写入 SLF4J MDC，供 Log4j2 PatternLayout 输出结构化字段。
 */
public final class TraceLogContext {

    public static final String TRACE_ID_MDC_KEY = TraceConstants.TRACE_ID_CONTEXT_KEY;

    /**
     * 工具类不允许实例化，MDC 操作都通过静态方法完成。
     */
    private TraceLogContext() {
    }

    /**
     * 读取当前线程 MDC 中的 traceId；没有写入时返回 null。
     */
    public static String currentTraceId() {
        return MDC.get(TRACE_ID_MDC_KEY);
    }

    /**
     * 写入合法 traceId 到 MDC；非法值会清理现有 traceId，避免污染后续日志。
     */
    public static void putTraceId(String traceId) {
        if (TraceIdSupport.isValid(traceId)) {
            MDC.put(TRACE_ID_MDC_KEY, traceId.trim());
            return;
        }
        clearTraceId();
    }

    /**
     * 清理当前线程 MDC 中的 traceId，通常在请求结束或 scope 关闭时调用。
     */
    public static void clearTraceId() {
        MDC.remove(TRACE_ID_MDC_KEY);
    }

    /**
     * 临时切换当前线程 traceId，并返回可关闭 scope；关闭后恢复旧值或清理空旧值。
     */
    public static Scope withTraceId(String traceId) {
        String previousTraceId = currentTraceId();
        putTraceId(traceId);
        return () -> restoreTraceId(previousTraceId);
    }

    /**
     * 恢复进入 scope 前的 traceId；旧值为空时执行清理而不是写入空字符串。
     */
    private static void restoreTraceId(String previousTraceId) {
        if (previousTraceId == null) {
            clearTraceId();
            return;
        }
        MDC.put(TRACE_ID_MDC_KEY, previousTraceId);
    }

    public interface Scope extends AutoCloseable {

        /**
         * 关闭 traceId scope 并恢复进入 scope 前的 MDC 状态；不抛受检异常，便于 try-with-resources 使用。
         */
        @Override
        void close();
    }
}
