package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 公共配置最近一次 rollout 主状态行。 */
public record PublicAgentConfigRolloutStatusRow(
        String rolloutId,
        String status,
        String branch,
        String commitHash,
        String failureReason,
        String supersedesRolloutId,
        String supersededByRolloutId,
        String supersedeReason,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
