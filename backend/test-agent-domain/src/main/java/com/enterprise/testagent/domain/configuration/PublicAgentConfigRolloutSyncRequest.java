package com.enterprise.testagent.domain.configuration;

/**
 * 某台服务器尚未完成的公共配置 Git 同步任务，用于广播丢失或服务重启后的补偿。
 */
public record PublicAgentConfigRolloutSyncRequest(
        String rolloutId,
        String branch,
        String commitHash,
        String traceId) {
}
