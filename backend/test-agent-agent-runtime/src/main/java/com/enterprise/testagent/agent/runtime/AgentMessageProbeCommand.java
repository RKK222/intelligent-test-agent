package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/** 通过预生成稳定消息 ID 探测投递是否已被远端受理。 */
public record AgentMessageProbeCommand(
        ExecutionNode node,
        String remoteSessionId,
        String messageId,
        String traceId) {

    public AgentMessageProbeCommand {
        Objects.requireNonNull(node, "node must not be null");
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        messageId = DomainValidation.requireText(messageId, "messageId");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
