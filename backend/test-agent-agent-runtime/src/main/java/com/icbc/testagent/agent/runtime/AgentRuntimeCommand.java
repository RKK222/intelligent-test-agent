package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Map;
import java.util.Objects;

/**
 * 受控代理 agent runtime JSON API 的通用命令。
 */
public record AgentRuntimeCommand(
        ExecutionNode node,
        String method,
        String path,
        String directory,
        String workspace,
        Map<String, String> query,
        Object body,
        String traceId) {

    /**
     * 校验 HTTP 方法、绝对路径和 traceId，query 固化为不可变 Map。
     */
    public AgentRuntimeCommand {
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
