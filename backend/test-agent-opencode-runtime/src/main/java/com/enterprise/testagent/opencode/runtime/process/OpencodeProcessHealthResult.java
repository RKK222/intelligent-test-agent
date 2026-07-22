package com.enterprise.testagent.opencode.runtime.process;

/**
 * 管理进程健康检测结果。
 */
public record OpencodeProcessHealthResult(
        boolean healthy,
        boolean managerProcessPresent,
        Long pid,
        String message) {

    /** 兼容旧调用方；健康结果表示 manager 已找到受管进程。 */
    public OpencodeProcessHealthResult(boolean healthy, String message) {
        this(healthy, healthy, null, message);
    }

    /**
     * 构造健康结果，message 为空时使用默认说明。
     */
    public static OpencodeProcessHealthResult healthy(String message) {
        return healthy(null, message);
    }

    /** 构造带 manager 实际 PID 的健康结果。 */
    public static OpencodeProcessHealthResult healthy(Long pid, String message) {
        return new OpencodeProcessHealthResult(
                true,
                true,
                pid,
                message == null || message.isBlank() ? "ok" : message);
    }

    /**
     * 构造不健康结果，message 为空时使用默认说明。
     */
    public static OpencodeProcessHealthResult unhealthy(String message) {
        return notRunning(message);
    }

    /** 构造 manager/PID 仍存在但 opencode HTTP 或配置健康失败的结果。 */
    public static OpencodeProcessHealthResult managedUnhealthy(Long pid, String message) {
        return new OpencodeProcessHealthResult(
                false,
                true,
                pid,
                message == null || message.isBlank() ? "unhealthy" : message);
    }

    /** 构造 manager 明确找不到受管进程或 PID 的结果。 */
    public static OpencodeProcessHealthResult notRunning(String message) {
        return new OpencodeProcessHealthResult(
                false,
                false,
                null,
                message == null || message.isBlank() ? "not running" : message);
    }
}
