package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 拒绝远端 agent Diff 的通用命令。
 */
public record AgentRejectDiffCommand(
        ExecutionNode node,
        String remoteSessionId,
        String directory,
        String workspace,
        String messageId,
        String partId,
        String traceId) {

    /**
     * 校验远端会话、消息和 traceId。
     */
    public AgentRejectDiffCommand {
        Objects.requireNonNull(node, "node must not be null");
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        directory = directory == null ? null : DomainValidation.requireText(directory, "directory");
        workspace = workspace == null || workspace.isBlank() ? null : workspace.trim();
        messageId = DomainValidation.requireText(messageId, "messageId");
        partId = partId == null || partId.isBlank() ? null : partId.trim();
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
