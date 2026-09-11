package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 用户×会话发送次数统计行，来自平台业务库聚合查询。
 */
public record AnalyticsSessionUsageRow(
        String userId,
        String username,
        String sessionId,
        String sessionTitle,
        long userMessageCount,
        Instant firstMessageAt,
        Instant lastMessageAt) {
}
