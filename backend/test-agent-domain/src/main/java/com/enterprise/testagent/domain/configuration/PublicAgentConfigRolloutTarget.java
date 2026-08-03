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
        boolean forceStop) {

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
                processPid, processStartedAt, baseUrl, retryCount, leaseUntil, leaseToken, traceId, false);
    }
}
