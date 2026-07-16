package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * Agent 配置 worktree 表行模型，对应 agent_config_worktrees 表。
 */
public record AgentConfigWorktreeRow(
        String worktreeId,
        String scope,
        String workspaceId,
        String linuxServerId,
        String worktreeName,
        String branch,
        String rootPath,
        String createdByUserId,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
