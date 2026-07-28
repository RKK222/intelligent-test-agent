package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/** 单个 generation 在目标服务器上的物化副本和租约快照。 */
public record AppSourceReplica(
        CodeRepositoryId repositoryId,
        long generation,
        LinuxServerId linuxServerId,
        WorkspaceId runtimeWorkspaceId,
        AppSourceReplicaStatus status,
        String leaseOwner,
        Instant leaseUntil,
        int attemptCount,
        Instant nextRetryAt,
        String safeErrorCode,
        String safeErrorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public AppSourceReplica {
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (generation < 1L || attemptCount < 0) {
            throw new IllegalArgumentException("generation must be positive and attemptCount must not be negative");
        }
        leaseOwner = optionalText(leaseOwner);
        safeErrorCode = optionalText(safeErrorCode);
        safeErrorMessage = optionalText(safeErrorMessage);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /** generation、owner 和未过期绝对租约共同构成副本 worker fencing。 */
    public boolean ownsLease(long expectedGeneration, String expectedOwner, Instant now) {
        return generation == expectedGeneration
                && expectedOwner != null
                && expectedOwner.equals(leaseOwner)
                && leaseUntil != null
                && now != null
                && now.isBefore(leaseUntil);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
