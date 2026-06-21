package com.icbc.testagent.opencode.client;

import com.icbc.testagent.domain.node.ExecutionNode;
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

    /**
     * 校验 projected messages 查询命令，并为 limit/order 提供安全默认值。
     */
    public OpencodeSessionMessagesCommand {
        node = Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = requireText(opencodeSessionId, "opencodeSessionId");
        limit = normalizeLimit(limit);
        order = normalizeOrder(order);
        traceId = requireText(traceId, "traceId");
    }

    /**
     * 将非法或过大的 limit 归一到平台允许范围，避免一次读取过多远端消息。
     */
    private static int normalizeLimit(int value) {
        if (value <= 0) {
            return 100;
        }
        return Math.min(value, 200);
    }

    /**
     * 规范化排序方向，缺省按 opencode messages API 的正序读取。
     */
    private static String normalizeOrder(String value) {
        if (value == null || value.isBlank()) {
            return "asc";
        }
        return value;
    }

    /**
     * 校验必填文本字段，保持 command 构造阶段失败。
     */
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
