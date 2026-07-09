package com.icbc.testagent.opencode.client;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 校验远端 opencode session 是否仍存在的内部命令。
 */
public record OpencodeSessionExistsCommand(
        ExecutionNode node,
        String opencodeSessionId,
        String traceId) {

    /**
     * 校验节点、远端 session id 和 traceId。
     */
    public OpencodeSessionExistsCommand {
        Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
