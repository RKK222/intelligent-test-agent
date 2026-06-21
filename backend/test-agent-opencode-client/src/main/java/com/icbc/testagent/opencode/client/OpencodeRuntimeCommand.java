package com.icbc.testagent.opencode.client;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Map;
import java.util.Objects;

/**
 * 受控 opencode runtime 调用命令，用于 Phase 11 新增 API 先通过 facade 暴露稳定 JSON projection。
 */
public record OpencodeRuntimeCommand(
        ExecutionNode node,
        String method,
        String path,
        String directory,
        String workspace,
        Map<String, String> query,
        Object body,
        String traceId) {

    /**
     * 校验 runtime 调用的 HTTP 方法、绝对路径和 traceId，并固化 query 参数。
     */
    public OpencodeRuntimeCommand {
        Objects.requireNonNull(node, "node must not be null");
        method = DomainValidation.requireText(method, "method").toUpperCase();
        path = DomainValidation.requireText(path, "path");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with /");
        }
        directory = directory == null ? null : DomainValidation.requireText(directory, "directory");
        workspace = workspace == null || workspace.isBlank() ? null : workspace.trim();
        query = query == null ? Map.of() : Map.copyOf(query);
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
