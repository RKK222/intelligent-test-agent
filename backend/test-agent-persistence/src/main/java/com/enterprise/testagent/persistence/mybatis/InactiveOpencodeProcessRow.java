package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** MyBatis 返回的闲置 OpenCode 进程及其最近活动时间。 */
public record InactiveOpencodeProcessRow(
        String processId,
        String userId,
        String linuxServerId,
        String containerId,
        int port,
        Long pid,
        String baseUrl,
        String status,
        String sessionPath,
        String configPath,
        Instant startedAt,
        Instant lastHealthCheckAt,
        String healthMessage,
        Instant createdAt,
        Instant updatedAt,
        String traceId,
        Instant lastActivityAt) {
}
