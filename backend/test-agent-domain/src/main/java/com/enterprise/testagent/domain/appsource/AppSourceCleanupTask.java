package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.time.Instant;
import java.util.Objects;

/** 单服务器绝对 deleteAt 清理任务；operation/snapshot 外键由数据库延迟校验。 */
public record AppSourceCleanupTask(
        String cleanupTaskId,
        String operationId,
        CodeRepositoryId repositoryId,
        long generation,
        LinuxServerId linuxServerId,
        Instant deleteAt,
        AppSourceCleanupStatus status,
        String leaseOwner,
        Instant leaseUntil,
        int attemptCount,
        Instant nextRetryAt,
        String safeErrorCode,
        String safeErrorMessage,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    public AppSourceCleanupTask {
        cleanupTaskId = requireText(cleanupTaskId, "cleanupTaskId");
        operationId = requireText(operationId, "operationId");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        deleteAt = Objects.requireNonNull(deleteAt, "deleteAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (generation < 1L || attemptCount < 0) {
            throw new IllegalArgumentException("generation must be positive and attemptCount must not be negative");
        }
        leaseOwner = optionalText(leaseOwner);
        safeErrorCode = optionalText(safeErrorCode);
        safeErrorMessage = optionalText(safeErrorMessage);
        traceId = requireText(traceId, "traceId");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public boolean ownsLease(String expectedOwner, Instant now) {
        return expectedOwner != null
                && expectedOwner.equals(leaseOwner)
                && leaseUntil != null
                && now != null
                && now.isBefore(leaseUntil);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
