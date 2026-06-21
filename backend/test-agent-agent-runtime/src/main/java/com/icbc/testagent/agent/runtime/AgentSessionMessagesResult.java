package com.icbc.testagent.agent.runtime;

import java.util.List;

/**
 * 远端 agent 会话消息投影结果。
 */
public record AgentSessionMessagesResult(List<AgentSessionMessage> messages) {

    /**
     * 固化消息列表。
     */
    public AgentSessionMessagesResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
