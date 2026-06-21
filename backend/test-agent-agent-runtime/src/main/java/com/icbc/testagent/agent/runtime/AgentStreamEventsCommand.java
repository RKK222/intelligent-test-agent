package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 订阅远端 agent 事件流的通用命令。
 */
public record AgentStreamEventsCommand(
        ExecutionNode node,
        RunId runId,
        String directory,
        String workspace,
        String traceId) {

    /**
     * 校验节点、Run 和 traceId。
     */
    public AgentStreamEventsCommand {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        directory = directory == null ? null : DomainValidation.requireText(directory, "directory");
        workspace = workspace == null || workspace.isBlank() ? null : workspace.trim();
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
