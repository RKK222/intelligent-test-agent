package com.enterprise.testagent.agent.runtime;

import java.util.List;

/**
 * 远端 agent 会话消息投影结果。
 */
public record AgentSessionMessagesResult(
        List<AgentSessionMessage> messages,
        String previousCursor,
        String nextCursor) {

    /**
     * 兼容无分页调用方，默认不暴露远端 cursor。
     */
    public AgentSessionMessagesResult(List<AgentSessionMessage> messages) {
        this(messages, null, null);
    }

    /**
     * 固化消息列表，cursor 保持远端不透明语义。
     */
    public AgentSessionMessagesResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
