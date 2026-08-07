package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/** 读取远端可重放用户轮次的中立命令。 */
public record AgentReplayableTurnCommand(
        ExecutionNode node,
        String remoteSessionId,
        String directory,
        String workspace,
        String messageId,
        String traceId) {

    public AgentReplayableTurnCommand {
        Objects.requireNonNull(node, "node must not be null");
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = optional(workspace);
        messageId = DomainValidation.requireText(messageId, "messageId");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
