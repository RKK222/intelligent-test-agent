package com.enterprise.testagent.domain.memory;

import java.time.Instant;
import java.util.Objects;

/** 成功根 Run 的学习 Outbox；只保存可恢复定位信息，不保存原始消息。 */
public record MemoryLearningJob(
        String jobId,
        String runId,
        String sessionId,
        String workspaceId,
        String userId,
        String applicationId,
        String agentId,
        String selectedModelId,
        String status,
        int attempts,
        Instant availableAt,
        String claimedBy,
        Instant leaseUntil,
        String lastErrorCode,
        Instant createdAt,
        Instant updatedAt) {

    public MemoryLearningJob {
        Objects.requireNonNull(availableAt, "availableAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
    }
}
