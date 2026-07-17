package com.enterprise.testagent.opencode.runtime.process;

/**
 * 管理进程健康检测结果。
 */
public record OpencodeProcessHealthResult(boolean healthy, String message) {

    /**
     * 构造健康结果，message 为空时使用默认说明。
     */
    public static OpencodeProcessHealthResult healthy(String message) {
        return new OpencodeProcessHealthResult(true, message == null || message.isBlank() ? "ok" : message);
    }

    /**
     * 构造不健康结果，message 为空时使用默认说明。
     */
    public static OpencodeProcessHealthResult unhealthy(String message) {
        return new OpencodeProcessHealthResult(false, message == null || message.isBlank() ? "unhealthy" : message);
    }
}
