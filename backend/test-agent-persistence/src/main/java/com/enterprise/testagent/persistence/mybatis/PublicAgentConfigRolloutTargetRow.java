package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 公共配置发布进程目标 MyBatis 行模型。
 */
public record PublicAgentConfigRolloutTargetRow(
        String targetId,
        String rolloutId,
        String userId,
        String linuxServerId,
        String containerId,
        int port,
        String baseUrl,
        int retryCount,
        Instant leaseUntil,
        String leaseToken,
        String traceId) {
}
