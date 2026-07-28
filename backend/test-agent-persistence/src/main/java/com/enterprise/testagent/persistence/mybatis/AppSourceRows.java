package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 应用源码 MyBatis 内部行模型，禁止跨出 persistence 模块。 */
public final class AppSourceRows {

    private AppSourceRows() {
    }

    public record SlotRow(
            String repositoryId,
            Long activeGeneration,
            Long pendingGeneration,
            long nextGeneration,
            String latestOperationId,
            long lockVersion,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record SnapshotRow(
            String repositoryId,
            long generation,
            String repositoryEnglishName,
            String purpose,
            String ownerUserId,
            String branch,
            String targetCommit,
            String selectedPathsJson,
            String indexSha256,
            Instant acceptedAt,
            Instant expiresAt,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ReplicaRow(
            String repositoryId,
            long generation,
            String linuxServerId,
            String runtimeWorkspaceId,
            String status,
            String leaseOwner,
            Instant leaseUntil,
            int attemptCount,
            Instant nextRetryAt,
            String safeErrorCode,
            String safeErrorMessage,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record OperationRow(
            String operationId,
            String appId,
            String repositoryId,
            Long sourceGeneration,
            long targetGeneration,
            String actorUserId,
            String operationType,
            String requestHash,
            String status,
            String traceId,
            Instant acceptedAt,
            Instant completedAt) {
    }

    public record StepRow(
            String stepId,
            String operationId,
            String scope,
            String linuxServerId,
            String stepCode,
            int sequence,
            String status,
            String safeSummary,
            Instant startedAt,
            Instant completedAt,
            Instant updatedAt) {
    }

    public record CleanupRow(
            String cleanupTaskId,
            String operationId,
            String repositoryId,
            long generation,
            String linuxServerId,
            Instant deleteAt,
            String status,
            String leaseOwner,
            Instant leaseUntil,
            int attemptCount,
            Instant nextRetryAt,
            String safeErrorCode,
            String safeErrorMessage,
            String traceId,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RecentRow(
            String userId,
            String appId,
            String repositoryId,
            long generation,
            Instant updatedAt) {
    }
}
