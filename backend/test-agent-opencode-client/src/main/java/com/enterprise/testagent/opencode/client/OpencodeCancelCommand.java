package com.enterprise.testagent.opencode.client;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * opencode 会话取消命令，session id 是远端 opencode session id。
 */
public record OpencodeCancelCommand(
        ExecutionNode node,
        String opencodeSessionId,
        String directory,
        String workspace,
        String traceId) {

    /**
     * 校验取消命令的远端 session、目录和 traceId，workspace 为空白时按未指定处理。
     */
    public OpencodeCancelCommand {
        Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = optionalText(workspace);
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    /**
     * 规范化可选文本，防止向 opencode query 传入空白 workspace。
     */
    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
