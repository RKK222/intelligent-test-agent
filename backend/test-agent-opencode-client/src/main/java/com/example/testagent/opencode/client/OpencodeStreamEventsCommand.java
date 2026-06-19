package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * opencode 事件订阅命令，stream 输出会被转换为平台 RunEventDraft。
 */
public record OpencodeStreamEventsCommand(
        ExecutionNode node,
        RunId runId,
        String directory,
        String workspace,
        String traceId) {

    public OpencodeStreamEventsCommand {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = DomainValidation.requireText(workspace, "workspace");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
