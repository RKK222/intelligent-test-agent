package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 拒绝 Run 级 Diff 的命令，对应 opencode sessionRevert。
 */
public record OpencodeRejectDiffCommand(
        ExecutionNode node,
        String opencodeSessionId,
        String directory,
        String workspace,
        String messageId,
        String partId,
        String traceId) {

    public OpencodeRejectDiffCommand {
        Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = optionalText(workspace);
        messageId = DomainValidation.requireText(messageId, "messageId");
        partId = optionalText(partId);
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
