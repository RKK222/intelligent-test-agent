package com.enterprise.testagent.domain.reference;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 引用资产库同步目标与总体状态；generation 是所有副本 worker 的 fencing 代次。 */
public record ReferenceRepositoryState(
        CodeRepositoryId repositoryId,
        String branch,
        String targetCommitHash,
        long generation,
        ReferenceRepositoryStatus status,
        ReferenceRepositoryOperationType operationType,
        UserId credentialUserId,
        String traceId,
        String lastError,
        Instant initializedAt,
        Instant createdAt,
        Instant updatedAt) {

    public ReferenceRepositoryState {
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(operationType, "operationType must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (generation < 0L) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        branch = normalize(branch);
        targetCommitHash = normalize(targetCommitHash);
        lastError = normalize(lastError);
        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    /** 兼容存量调用；新业务流必须显式传入 operationType。 */
    public ReferenceRepositoryState(
            CodeRepositoryId repositoryId,
            String branch,
            String targetCommitHash,
            long generation,
            ReferenceRepositoryStatus status,
            UserId credentialUserId,
            String traceId,
            String lastError,
            Instant initializedAt,
            Instant createdAt,
            Instant updatedAt) {
        this(repositoryId, branch, targetCommitHash, generation, status, defaultOperation(status), credentialUserId,
                traceId, lastError, initializedAt, createdAt, updatedAt);
    }

    private static ReferenceRepositoryOperationType defaultOperation(ReferenceRepositoryStatus status) {
        return switch (status) {
            case INITIALIZING, UNINITIALIZED -> ReferenceRepositoryOperationType.INITIALIZE;
            case VERIFYING -> ReferenceRepositoryOperationType.VERIFY_POINTERS;
            default -> ReferenceRepositoryOperationType.SYNCHRONIZE;
        };
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
