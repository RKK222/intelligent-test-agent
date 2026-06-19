package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * opencode 异步启动 Run 命令，使用远端 opencode session id，generated request 只在 gateway 内部构造。
 */
public record OpencodeStartRunCommand(
        ExecutionNode node,
        String opencodeSessionId,
        String directory,
        String workspace,
        String prompt,
        String traceId) {

    public OpencodeStartRunCommand {
        Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = optionalText(workspace);
        prompt = DomainValidation.requireText(prompt, "prompt");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
