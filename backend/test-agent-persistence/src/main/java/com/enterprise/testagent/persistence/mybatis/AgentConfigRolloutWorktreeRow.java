package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 应用配置发布个人 worktree 补偿任务的 MyBatis 构造映射行。
 */
public record AgentConfigRolloutWorktreeRow(
        String rolloutId,
        String versionId,
        String personalWorkspaceId,
        String userId,
        String linuxServerId,
        String targetCommit,
        String traceId,
        int retryCount,
        Instant leaseUntil,
        String leaseToken) {
}
