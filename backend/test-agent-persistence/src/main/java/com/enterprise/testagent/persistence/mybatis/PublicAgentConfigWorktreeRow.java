package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 公共个人 worktree 补偿任务的 MyBatis 构造映射行。 */
public record PublicAgentConfigWorktreeRow(
        String rolloutId,
        String worktreeId,
        String userId,
        String linuxServerId,
        String targetCommit,
        String traceId,
        int retryCount,
        Instant leaseUntil,
        String leaseToken) {
}
