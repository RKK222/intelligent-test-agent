package com.icbc.testagent.opencode.client;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
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

    /**
     * 校验 Diff 查询的远端 session、目录和 traceId，messageId 为空时查询当前 Diff。
     */
    public OpencodeDiffCommand {
        Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = optionalText(workspace);
        messageId = optionalText(messageId);
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    /**
     * 规范化可选文本，空白字符串按缺失处理。
     */
    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
