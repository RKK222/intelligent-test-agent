package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 读取远端 agent 会话消息投影的通用命令。
 */
public record AgentSessionMessagesCommand(
        ExecutionNode node,
        String remoteSessionId,
        int limit,
        String order,
        String cursor,
        String traceId) {

    /**
     * 校验远端会话、分页上限和 traceId。
     */
    public AgentSessionMessagesCommand {
        Objects.requireNonNull(node, "node must not be null");
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        order = order == null || order.isBlank() ? "asc" : order.trim();
        cursor = cursor == null || cursor.isBlank() ? null : cursor.trim();
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
