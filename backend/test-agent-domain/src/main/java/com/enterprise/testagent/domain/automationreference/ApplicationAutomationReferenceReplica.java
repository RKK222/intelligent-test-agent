package com.enterprise.testagent.domain.automationreference;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 单个应用自动化引用代次在一台服务器上的共享只读副本任务。 */
public record ApplicationAutomationReferenceReplica(
        ApplicationId appId,
        CodeRepositoryId repositoryId,
        long generation,
        LinuxServerId linuxServerId,
        ReferenceRepositoryReplicaStatus status,
        String currentBranch,
        String currentCommitHash,
        int retryCount,
        Instant nextRetryAt,
        String leaseToken,
        Instant leaseUntil,
        String lastError,
        Instant syncedAt,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt) {

    public ApplicationAutomationReferenceReplica {
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (generation < 1L || retryCount < 0) {
            throw new IllegalArgumentException("generation must be positive and retryCount must not be negative");
        }
        currentBranch = normalize(currentBranch);
        currentCommitHash = normalize(currentCommitHash);
        leaseToken = normalize(leaseToken);
        lastError = normalize(lastError);
    }

    public static Duration retryDelay(int retryCount) {
        int normalized = Math.max(1, retryCount);
        long seconds = 5L << Math.min(16, normalized - 1);
        return Duration.ofSeconds(Math.min(300L, seconds));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
