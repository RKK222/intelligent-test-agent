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
        UserId credentialUserId,
        String traceId,
        String lastError,
        Instant initializedAt,
        Instant createdAt,
        Instant updatedAt) {

    public ReferenceRepositoryState {
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(status, "status must not be null");
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

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
