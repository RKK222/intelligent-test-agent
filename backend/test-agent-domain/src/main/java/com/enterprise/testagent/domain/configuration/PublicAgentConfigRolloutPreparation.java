package com.enterprise.testagent.domain.configuration;

import java.time.Instant;

/**
 * 已持久化但尚未确认远端提交的公共配置发布；发起服务器据此恢复 push/拉取后的中断窗口。
 */
public record PublicAgentConfigRolloutPreparation(
        String rolloutId,
        String branch,
        String expectedCommitHash,
        String previousCommitHash,
        String initiatedByUserId,
        String initiatedLinuxServerId,
        String traceId,
        Instant createdAt) {
}
