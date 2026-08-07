package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/** 仅在已确认新消息未投递时撤销回退的中立命令。 */
public record AgentUnrevertTurnCommand(
        ExecutionNode node,
        String remoteSessionId,
        String directory,
        String workspace,
        String traceId) {

    public AgentUnrevertTurnCommand {
        Objects.requireNonNull(node, "node must not be null");
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = workspace == null || workspace.isBlank() ? null : workspace.trim();
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
