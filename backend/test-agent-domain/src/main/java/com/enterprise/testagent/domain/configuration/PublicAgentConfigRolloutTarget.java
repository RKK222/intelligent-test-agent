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
        String traceId) {
}
