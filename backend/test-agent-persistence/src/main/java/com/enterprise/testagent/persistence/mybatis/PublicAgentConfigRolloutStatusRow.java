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
        Instant completedAt,
        String configScope,
        String scopeKey) {

    /** 兼容公共发布仓储测试的旧行模型。 */
    public PublicAgentConfigRolloutStatusRow(
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
        this(
                rolloutId, status, branch, commitHash, failureReason,
                supersedesRolloutId, supersededByRolloutId, supersedeReason,
                createdAt, updatedAt, completedAt, "PUBLIC", null);
    }
}
