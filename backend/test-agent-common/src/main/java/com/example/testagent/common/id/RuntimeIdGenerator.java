package com.example.testagent.common.id;

import java.util.UUID;

/**
 * Runtime API 使用的业务 ID 生成器，统一生成带领域前缀的外部可见 ID。
 */
public final class RuntimeIdGenerator {

    /**
     * 工具类不允许实例化，所有 ID 生成入口都通过静态方法暴露。
     */
    private RuntimeIdGenerator() {
    }

    /**
     * 生成 Workspace 外部 ID，返回值固定使用 `wrk_` 前缀，便于 API 与日志中识别资源类型。
     */
    public static String workspaceId() {
        return prefixed("wrk_");
    }

    /**
     * 生成平台 Session ID，返回值只表示平台会话，不可与远端 opencode session id 混用。
     */
    public static String sessionId() {
        return prefixed("ses_");
    }

    /**
     * 生成 Run ID，供运行编排、事件流和 Diff 操作共同引用同一次运行。
     */
    public static String runId() {
        return prefixed("run_");
    }

    /**
     * 生成会话消息 ID，供平台持久化消息和恢复投影时稳定定位消息。
     */
    public static String messageId() {
        return prefixed("msg_");
    }

    /**
     * 生成 PTY ticket ID，返回值仅用于短生命周期终端连接授权。
     */
    public static String terminalTicketId() {
        return prefixed("pty_");
    }

    /**
     * 生成用户外部 ID，返回值固定使用 {@code usr_} 前缀。
     */
    public static String userId() {
        return prefixed("usr_");
    }

    /**
     * 生成登录日志 ID，返回值固定使用 {@code log_} 前缀。
     */
    public static String logId() {
        return prefixed("log_");
    }

    /**
     * 生成字典 ID，返回值固定使用 {@code dict_} 前缀。
     */
    public static String dictId() {
        return prefixed("dict_");
    }

    /**
     * 按给定领域前缀拼接无横线 UUID；调用方必须传入已约定的稳定前缀。
     */
    private static String prefixed(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
