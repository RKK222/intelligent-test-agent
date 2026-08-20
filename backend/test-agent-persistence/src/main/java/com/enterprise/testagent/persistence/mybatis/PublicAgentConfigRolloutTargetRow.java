package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 公共配置发布进程目标 MyBatis 行模型。
 */
public record PublicAgentConfigRolloutTargetRow(
        String targetId,
        String rolloutId,
        String configScope,
        String userId,
        String linuxServerId,
        String containerId,
        int port,
        Long processPid,
        Instant processStartedAt,
        String baseUrl,
        int retryCount,
        Instant leaseUntil,
        String leaseToken,
        String traceId,
        boolean forceStop,
        String previousCommitHash,
        String commitHash,
        String scopeKey) {

    /** 兼容已携带提交坐标但尚未携带应用版本范围的调用。 */
    public PublicAgentConfigRolloutTargetRow(
            String targetId,
            String rolloutId,
            String configScope,
            String userId,
            String linuxServerId,
            String containerId,
            int port,
            Long processPid,
            Instant processStartedAt,
            String baseUrl,
            int retryCount,
            Instant leaseUntil,
            String leaseToken,
            String traceId,
            boolean forceStop,
            String previousCommitHash,
            String commitHash) {
        this(
                targetId, rolloutId, configScope, userId, linuxServerId, containerId, port,
                processPid, processStartedAt, baseUrl, retryCount, leaseUntil, leaseToken, traceId,
                forceStop, previousCommitHash, commitHash, null);
    }

    /** 兼容不校验发布提交坐标的仓储单元测试。 */
    public PublicAgentConfigRolloutTargetRow(
            String targetId,
            String rolloutId,
            String configScope,
            String userId,
            String linuxServerId,
            String containerId,
            int port,
            Long processPid,
            Instant processStartedAt,
            String baseUrl,
            int retryCount,
            Instant leaseUntil,
            String leaseToken,
            String traceId,
            boolean forceStop) {
        this(
                targetId, rolloutId, configScope, userId, linuxServerId, containerId, port,
                processPid, processStartedAt, baseUrl, retryCount, leaseUntil, leaseToken, traceId,
                forceStop, null, null, null);
    }
}
