package com.enterprise.testagent.agent.runtime;

import java.util.List;
import java.util.Map;

/**
 * 远端 agent 会话消息投影，用于 SSE 建连恢复 transient 消息快照。
 */
public record AgentSessionMessage(
        Map<String, Object> message,
        List<Map<String, Object>> parts) {

    /**
     * 固化 message 和 part 列表，避免调用方修改恢复快照。
     */
    public AgentSessionMessage {
        message = message == null ? Map.of() : Map.copyOf(message);
        parts = parts == null ? List.of() : parts.stream()
                .map(part -> part == null ? Map.<String, Object>of() : Map.copyOf(part))
                .toList();
    }
}
