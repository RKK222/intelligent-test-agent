package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * opencode 会话取消命令，directory/workspace 是透传给 opencode 的位置上下文。
 */
public record OpencodeCancelCommand(
        ExecutionNode node,
        SessionId sessionId,
        String directory,
        String workspace,
        String traceId) {

    public OpencodeCancelCommand {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = DomainValidation.requireText(workspace, "workspace");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
