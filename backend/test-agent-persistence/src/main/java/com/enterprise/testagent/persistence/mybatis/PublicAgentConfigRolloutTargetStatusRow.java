package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 公共 Agent/Skill rollout 待排空目标的 MyBatis 只读行。 */
public record PublicAgentConfigRolloutTargetStatusRow(
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
