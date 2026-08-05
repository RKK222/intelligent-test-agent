package com.enterprise.testagent.domain.configuration;

import java.time.Instant;

/**
 * 公共 Agent/Skill rollout 中尚未排空的单个用户进程明细，供超级管理员定位阻塞目标。
 */
public record PublicAgentConfigRolloutTargetStatus(
        String targetId,
        String userId,
        String username,
        String linuxServerId,
        String containerId,
        int port,
        Long processPid,
        Instant processStartedAt,
        String status,
        int retryCount,
        Instant nextRetryAt,
        String lastError,
        boolean forceStop,
        Instant updatedAt) {
}
