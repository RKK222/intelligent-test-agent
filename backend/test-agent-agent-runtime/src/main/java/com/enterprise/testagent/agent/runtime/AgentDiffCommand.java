package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 查询远端 agent Diff 的通用命令。
 */
public record AgentDiffCommand(
        ExecutionNode node,
        String remoteSessionId,
        String directory,
        String workspace,
        String messageId,
        String traceId) {

    /**
     * 校验远端会话和 traceId。
     */
    public AgentDiffCommand {
        Objects.requireNonNull(node, "node must not be null");
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        directory = directory == null ? null : DomainValidation.requireText(directory, "directory");
        workspace = workspace == null || workspace.isBlank() ? null : workspace.trim();
        messageId = messageId == null || messageId.isBlank() ? null : messageId.trim();
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
