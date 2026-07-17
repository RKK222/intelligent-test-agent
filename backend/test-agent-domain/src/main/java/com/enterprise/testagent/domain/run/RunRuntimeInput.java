package com.enterprise.testagent.domain.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Redis 中的本轮完整输入；该对象不得写入 PostgreSQL 或日志。 */
public record RunRuntimeInput(
        RunId runId,
        String prompt,
        List<Map<String, Object>> parts,
        String messageId,
        Instant createdAt,
        String workspaceRootPath,
        String executionNodeBaseUrl) {

    /** 兼容旧构造器；旧运行态没有可信目录时不能执行自动接管。 */
    public RunRuntimeInput(
            RunId runId,
            String prompt,
            List<Map<String, Object>> parts,
            String messageId,
            Instant createdAt) {
        this(runId, prompt, parts, messageId, createdAt, null, null);
    }

    /** 兼容只提供可信工作区路径的构造器。 */
    public RunRuntimeInput(
            RunId runId,
            String prompt,
            List<Map<String, Object>> parts,
            String messageId,
            Instant createdAt,
            String workspaceRootPath) {
        this(runId, prompt, parts, messageId, createdAt, workspaceRootPath, null);
    }

    public RunRuntimeInput {
        Objects.requireNonNull(runId, "runId must not be null");
        prompt = prompt == null ? "" : prompt;
        parts = parts == null ? List.of() : parts.stream().map(Map::copyOf).toList();
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        workspaceRootPath = workspaceRootPath == null || workspaceRootPath.isBlank()
                ? null
                : workspaceRootPath.trim();
        executionNodeBaseUrl = executionNodeBaseUrl == null || executionNodeBaseUrl.isBlank()
                ? null
                : executionNodeBaseUrl.trim();
    }
}
