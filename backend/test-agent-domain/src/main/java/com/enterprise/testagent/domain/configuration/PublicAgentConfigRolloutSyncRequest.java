package com.enterprise.testagent.domain.configuration;

import java.time.Instant;

/**
 * 某台服务器尚未完成的公共配置 Git 同步任务，用于广播丢失或服务重启后的补偿。
 */
public record PublicAgentConfigRolloutSyncRequest(
        String rolloutId,
        AgentConfigRolloutScope scope,
        String scopeKey,
        String branch,
        String commitHash,
        String initiatedByUserId,
        String traceId,
        int retryCount,
        Instant leaseUntil,
        String leaseToken) {

    /** 兼容存量公共配置测试和调用方；新应用范围必须显式传 scope/scopeKey。 */
    public PublicAgentConfigRolloutSyncRequest(
            String rolloutId,
            String branch,
            String commitHash,
            String initiatedByUserId,
            String traceId,
            int retryCount,
            Instant leaseUntil,
            String leaseToken) {
        this(
                rolloutId,
                AgentConfigRolloutScope.PUBLIC,
                null,
                branch,
                commitHash,
                initiatedByUserId,
                traceId,
                retryCount,
                leaseUntil,
                leaseToken);
    }
}
