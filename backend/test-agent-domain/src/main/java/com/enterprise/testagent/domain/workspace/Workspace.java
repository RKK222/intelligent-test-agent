package com.enterprise.testagent.domain.workspace;

import com.enterprise.testagent.domain.support.DomainValidation;
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
        String linuxServerId,
        String traceId) {

    /**
     * 创建 ACTIVE 工作区，使用占位 traceId 兼容简单测试和历史构造路径。
     */
    public Workspace(WorkspaceId workspaceId, String name, String rootPath, Instant createdAt) {
        this(workspaceId, name, rootPath, WorkspaceStatus.ACTIVE, createdAt, createdAt, null, "trace_unspecified");
    }

    /**
     * 兼容历史构造路径；历史工作区可能尚未绑定服务器，由路由层在同服务器校验后补齐。
     */
    public Workspace(
            WorkspaceId workspaceId,
            String name,
            String rootPath,
            WorkspaceStatus status,
            Instant createdAt,
            Instant updatedAt,
            String traceId) {
        this(workspaceId, name, rootPath, status, createdAt, updatedAt, null, traceId);
    }

    /**
     * 返回补齐服务器归属后的工作区副本，供 legacy 数据在安全校验成功后回填。
     */
    public Workspace withLinuxServerId(String linuxServerId, String traceId, Instant updatedAt) {
        return new Workspace(workspaceId, name, rootPath, status, createdAt, updatedAt, linuxServerId, traceId);
    }

    /**
     * 返回替换根路径后的工作区副本。用于 API/业务执行前把托管逻辑路径解析为物理路径，不代表持久化字段变更。
     */
    public Workspace withRootPath(String rootPath) {
        return new Workspace(workspaceId, name, rootPath, status, createdAt, updatedAt, linuxServerId, traceId);
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
        linuxServerId = normalizeOptional(linuxServerId);
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
