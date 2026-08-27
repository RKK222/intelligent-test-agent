package com.enterprise.testagent.localclient;

import java.util.UUID;

/** 客户端单次 JVM 运行的安全诊断上下文，供所有工作线程关联同一启动会话。 */
final class LocalClientDiagnostics {

    private static final String SESSION_PROPERTY = "testagent.localclient.sessionId";

    private LocalClientDiagnostics() {
    }

    /**
     * 在日志框架初始化前冻结本次启动会话 ID；外部传入的非法值不会进入日志模板。
     */
    static String ensureSessionId() {
        String existing = System.getProperty(SESSION_PROPERTY);
        if (existing != null && existing.matches("trace_client_[a-f0-9]{32}")) {
            return existing;
        }
        String generated = "trace_client_" + UUID.randomUUID().toString().replace("-", "");
        System.setProperty(SESSION_PROPERTY, generated);
        return generated;
    }

    /** 只返回根异常类型，避免异常 message 携带凭据、服务端正文或用户路径。 */
    static String rootFailureType(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    /** 单调时钟耗时统一换算为毫秒，不使用墙上时间计算阶段耗时。 */
    static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
