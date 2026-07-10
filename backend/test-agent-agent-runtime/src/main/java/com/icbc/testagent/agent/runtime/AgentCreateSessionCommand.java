package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 创建远端 agent 会话的通用命令。
 */
public record AgentCreateSessionCommand(
        ExecutionNode node,
        String directory,
        String workspace,
        String title,
        String traceId) {

    /**
     * 校验节点和 traceId；标题为空时不透传，交由远端使用默认标题。
     */
    public AgentCreateSessionCommand {
        Objects.requireNonNull(node, "node must not be null");
        directory = directory == null ? null : DomainValidation.requireText(directory, "directory");
        workspace = workspace == null || workspace.isBlank() ? null : workspace.trim();
        title = title == null || title.isBlank() ? null : title;
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
