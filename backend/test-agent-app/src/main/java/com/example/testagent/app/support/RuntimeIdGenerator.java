package com.example.testagent.app.support;

import java.util.UUID;

/**
 * Runtime API 使用的业务 ID 生成器，统一生成带领域前缀的外部可见 ID。
 */
public final class RuntimeIdGenerator {

    private RuntimeIdGenerator() {
    }

    public static String workspaceId() {
        return prefixed("wrk_");
    }

    public static String sessionId() {
        return prefixed("ses_");
    }

    public static String runId() {
        return prefixed("run_");
    }

    public static String messageId() {
        return prefixed("msg_");
    }

    public static String terminalTicketId() {
        return prefixed("pty_");
    }

    private static String prefixed(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
