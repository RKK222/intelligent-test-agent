package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 取消远端 agent 会话运行的通用命令。
 */
public record AgentCancelCommand(
        ExecutionNode node,
        String remoteSessionId,
        String directory,
        String workspace,
        String traceId) {

    /**
     * 校验远端会话和 traceId。
     */
    public AgentCancelCommand {
        Objects.requireNonNull(node, "node must not be null");
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        directory = directory == null ? null : DomainValidation.requireText(directory, "directory");
        workspace = workspace == null || workspace.isBlank() ? null : workspace.trim();
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
