package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/** 按用户消息边界执行完整原生回退的中立命令。 */
public record AgentRevertTurnCommand(
        ExecutionNode node,
        String remoteSessionId,
        String directory,
        String workspace,
        String messageId,
        String traceId) {

    public AgentRevertTurnCommand {
        Objects.requireNonNull(node, "node must not be null");
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = workspace == null || workspace.isBlank() ? null : workspace.trim();
        messageId = DomainValidation.requireText(messageId, "messageId");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
