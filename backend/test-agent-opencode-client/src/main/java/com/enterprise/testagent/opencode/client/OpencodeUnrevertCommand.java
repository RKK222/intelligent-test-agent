package com.enterprise.testagent.opencode.client;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/** 调用 OpenCode 原生 session unrevert 的稳定门面命令。 */
public record OpencodeUnrevertCommand(
        ExecutionNode node,
        String opencodeSessionId,
        String directory,
        String workspace,
        String traceId) {

    public OpencodeUnrevertCommand {
        Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = workspace == null || workspace.isBlank() ? null : workspace.trim();
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
