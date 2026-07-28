package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 公共配置 rollout 的单服务器同步与排空聚合行。 */
public record PublicAgentConfigRolloutServerStatusRow(
        String linuxServerId,
        String syncStatus,
        int retryCount,
        long targetTotal,
        long targetPending,
        long targetDisposed,
        long targetAbandoned,
        long worktreeTotal,
        long worktreePending,
        long worktreeSynced,
        String lastError,
        Instant syncedAt,
        Instant updatedAt) {

    public PublicAgentConfigRolloutServerStatusRow(
            String linuxServerId,
            String syncStatus,
            int retryCount,
            long targetTotal,
            long targetPending,
            long targetDisposed,
            long targetAbandoned,
            String lastError,
            Instant syncedAt,
            Instant updatedAt) {
        this(
                linuxServerId,
                syncStatus,
                retryCount,
                targetTotal,
                targetPending,
                targetDisposed,
                targetAbandoned,
                0,
                0,
                0,
                lastError,
                syncedAt,
                updatedAt);
    }
}
