package com.enterprise.testagent.domain.configuration;

import java.time.Instant;

/**
 * 一次公共配置发布中需要等待 Session 空闲并 dispose 的 opencode 进程快照。
 */
public record PublicAgentConfigRolloutTarget(
        String targetId,
        String rolloutId,
        AgentConfigRolloutScope configScope,
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
    public PublicAgentConfigRolloutTarget(
            String targetId,
            String rolloutId,
            AgentConfigRolloutScope configScope,
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

    /** 兼容未携带发布提交坐标的既有调用；运行态影响判断会保持 dispose。 */
    public PublicAgentConfigRolloutTarget(
            String targetId,
            String rolloutId,
            AgentConfigRolloutScope configScope,
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

    /** 非纠错发布沿用既有构造语义；纠错标记由认领 SQL 从 rollout 审计链派生。 */
    public PublicAgentConfigRolloutTarget(
            String targetId,
            String rolloutId,
            AgentConfigRolloutScope configScope,
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
            String traceId) {
        this(
                targetId, rolloutId, configScope, userId, linuxServerId, containerId, port,
                processPid, processStartedAt, baseUrl, retryCount, leaseUntil, leaseToken, traceId,
                false, null, null, null);
    }
}
