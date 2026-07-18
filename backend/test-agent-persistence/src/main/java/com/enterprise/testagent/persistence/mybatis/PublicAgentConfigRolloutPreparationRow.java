package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 公共配置发布 PREPARING 行模型。 */
public record PublicAgentConfigRolloutPreparationRow(
        String rolloutId,
        String scope,
        String scopeKey,
        String branch,
        String expectedCommitHash,
        String previousCommitHash,
        String initiatedByUserId,
        String initiatedLinuxServerId,
        String traceId,
        Instant createdAt) {
}
