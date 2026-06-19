package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 查询 opencode 会话 Diff 的命令，messageId 可为空以读取远端当前快照 Diff。
 */
public record OpencodeDiffCommand(
        ExecutionNode node,
        String opencodeSessionId,
        String directory,
        String workspace,
        String messageId,
        String traceId) {

    public OpencodeDiffCommand {
        Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = optionalText(workspace);
        messageId = optionalText(messageId);
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
