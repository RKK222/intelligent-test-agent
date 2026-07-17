package com.enterprise.testagent.persistence.mybatis;

/** 公共配置发布待补偿 Git 同步行模型。 */
public record PublicAgentConfigRolloutSyncRow(
        String rolloutId,
        String branch,
        String commitHash,
        String traceId) {
}
