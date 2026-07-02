package com.icbc.testagent.opencode.client;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * opencode 事件订阅命令，stream 输出会被转换为平台 RunEventDraft。
 */
public record OpencodeStreamEventsCommand(
        ExecutionNode node,
        RunId runId,
        String opencodeSessionId,
        String directory,
        String workspace,
        String traceId) {

    /**
     * 校验事件订阅命令的执行节点、Run、目录和 traceId，workspace 为空时不传远端。
     */
    public OpencodeStreamEventsCommand {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = optionalText(workspace);
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    /**
     * 规范化可选文本，空白字符串按缺失处理。
     */
    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
