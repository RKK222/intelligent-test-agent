package com.icbc.testagent.domain.workspace;

import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 工作区领域对象，仅表达平台管理的工作目录，不包含文件系统访问实现。
 */
public record Workspace(
        WorkspaceId workspaceId,
        String name,
        String rootPath,
        WorkspaceStatus status,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 创建 ACTIVE 工作区，使用占位 traceId 兼容简单测试和历史构造路径。
     */
    public Workspace(WorkspaceId workspaceId, String name, String rootPath, Instant createdAt) {
        this(workspaceId, name, rootPath, WorkspaceStatus.ACTIVE, createdAt, createdAt, "trace_unspecified");
    }

    /**
     * 校验工作区领域对象的不变量，确保路径、名称、状态、时间和 traceId 均有效。
     */
    public Workspace {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        name = DomainValidation.requireText(name, "name");
        rootPath = DomainValidation.requireText(rootPath, "rootPath");
        Objects.requireNonNull(status, "status must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
