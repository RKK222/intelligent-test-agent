package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 创建远端 opencode 会话命令；workspace query 仅在接入 opencode workspace 控制面时传入。
 */
public record OpencodeCreateSessionCommand(
        ExecutionNode node,
        String directory,
        String workspace,
        String title,
        String traceId) {

    public OpencodeCreateSessionCommand {
        Objects.requireNonNull(node, "node must not be null");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = optionalText(workspace);
        title = DomainValidation.requireText(title, "title");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
