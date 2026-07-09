package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 校验远端 agent session 是否仍存在的通用命令。
 */
public record AgentSessionExistsCommand(
        ExecutionNode node,
        String remoteSessionId,
        String traceId) {

    /**
     * 校验节点、远端 session id 和 traceId。
     */
    public AgentSessionExistsCommand {
        Objects.requireNonNull(node, "node must not be null");
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
