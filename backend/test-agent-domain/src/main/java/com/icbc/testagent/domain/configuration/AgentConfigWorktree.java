package com.icbc.testagent.domain.configuration;

import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * Agent 配置临时 worktree 记录，用于直接编辑模式以外的隔离修改和发布。
 */
public record AgentConfigWorktree(
        String worktreeId,
        AgentConfigScope scope,
        WorkspaceId workspaceId,
        String worktreeName,
        String branch,
        String rootPath,
        UserId createdBy,
        AgentConfigWorktreeStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public AgentConfigWorktree {
        worktreeId = DomainValidation.requireText(worktreeId, "worktreeId").trim();
        Objects.requireNonNull(scope, "scope must not be null");
        worktreeName = DomainValidation.requireText(worktreeName, "worktreeName").trim();
        branch = DomainValidation.requireText(branch, "branch").trim();
        rootPath = DomainValidation.requireText(rootPath, "rootPath").trim();
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(status, "status must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public AgentConfigWorktree markPublished(Instant now) {
        return new AgentConfigWorktree(
                worktreeId,
                scope,
                workspaceId,
                worktreeName,
                branch,
                rootPath,
                createdBy,
                AgentConfigWorktreeStatus.PUBLISHED,
                createdAt,
                now);
    }
}
