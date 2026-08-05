package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.List;

/**
 * 公共 Agent/Skill 全局 rollout 中单台服务器的 Git 同步与进程排空快照。
 */
public record PublicAgentConfigRolloutServerStatus(
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
        Instant updatedAt,
        List<PublicAgentConfigRolloutTargetStatus> pendingTargets) {

    public PublicAgentConfigRolloutServerStatus {
        pendingTargets = pendingTargets == null ? List.of() : List.copyOf(pendingTargets);
    }

    /** 兼容尚未携带待排空用户明细的存量调用。 */
    public PublicAgentConfigRolloutServerStatus(
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
        this(
                linuxServerId,
                syncStatus,
                retryCount,
                targetTotal,
                targetPending,
                targetDisposed,
                targetAbandoned,
                worktreeTotal,
                worktreePending,
                worktreeSynced,
                lastError,
                syncedAt,
                updatedAt,
                List.of());
    }

    /** 兼容只含 Git 同步与进程排空计数的存量调用。 */
    public PublicAgentConfigRolloutServerStatus(
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
                updatedAt,
                List.of());
    }
}
