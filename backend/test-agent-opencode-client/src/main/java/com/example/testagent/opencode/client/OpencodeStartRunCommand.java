package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * opencode 异步启动 Run 命令，prompt 是平台输入，generated request 只在 gateway 内部构造。
 */
public record OpencodeStartRunCommand(
        ExecutionNode node,
        SessionId sessionId,
        String directory,
        String workspace,
        String prompt,
        String traceId) {

    public OpencodeStartRunCommand {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = DomainValidation.requireText(workspace, "workspace");
        prompt = DomainValidation.requireText(prompt, "prompt");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
