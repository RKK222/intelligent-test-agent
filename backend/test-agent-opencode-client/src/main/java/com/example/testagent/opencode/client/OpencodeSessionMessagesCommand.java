package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import java.util.Objects;

/**
 * 读取 opencode projected messages 的 facade 命令，只暴露平台稳定参数。
 */
public record OpencodeSessionMessagesCommand(
        ExecutionNode node,
        String opencodeSessionId,
        int limit,
        String order,
        String cursor,
        String traceId) {

    public OpencodeSessionMessagesCommand {
        node = Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = requireText(opencodeSessionId, "opencodeSessionId");
        limit = normalizeLimit(limit);
        order = normalizeOrder(order);
        traceId = requireText(traceId, "traceId");
    }

    private static int normalizeLimit(int value) {
        if (value <= 0) {
            return 100;
        }
        return Math.min(value, 200);
    }

    private static String normalizeOrder(String value) {
        if (value == null || value.isBlank()) {
            return "asc";
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
