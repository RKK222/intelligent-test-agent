package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 公共配置发布待补偿 Git 同步行模型。 */
public record PublicAgentConfigRolloutSyncRow(
        String rolloutId,
        String scope,
        String scopeKey,
        String branch,
        String commitHash,
        String initiatedByUserId,
        String traceId,
        int retryCount,
        Instant leaseUntil,
        String leaseToken) {
}
