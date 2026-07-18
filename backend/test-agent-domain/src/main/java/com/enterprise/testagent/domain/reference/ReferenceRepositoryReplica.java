package com.enterprise.testagent.domain.reference;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 引用资产库在单台 Linux 服务器上的副本任务与数据库租约。 */
public record ReferenceRepositoryReplica(
        CodeRepositoryId repositoryId,
        LinuxServerId linuxServerId,
        long generation,
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

    public ReferenceRepositoryReplica {
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (generation < 0L || retryCount < 0) {
            throw new IllegalArgumentException("generation and retryCount must not be negative");
        }
        currentBranch = normalize(currentBranch);
        currentCommitHash = normalize(currentCommitHash);
        leaseToken = normalize(leaseToken);
        lastError = normalize(lastError);
    }

    /** 兼容存量构造；旧副本尚未经过主动核验。 */
    public ReferenceRepositoryReplica(
            CodeRepositoryId repositoryId,
            LinuxServerId linuxServerId,
            long generation,
            ReferenceRepositoryReplicaStatus status,
            String currentBranch,
            String currentCommitHash,
            int retryCount,
            Instant nextRetryAt,
            String leaseToken,
            Instant leaseUntil,
            String lastError,
            Instant syncedAt,
            Instant createdAt,
            Instant updatedAt) {
        this(repositoryId, linuxServerId, generation, status, currentBranch, currentCommitHash, retryCount,
                nextRetryAt, leaseToken, leaseUntil, lastError, syncedAt, null, createdAt, updatedAt);
    }

    /** 临时 Git/网络错误从五秒开始指数退避，最高五分钟。 */
    public static Duration retryDelay(int retryCount) {
        int normalized = Math.max(1, retryCount);
        long seconds = 5L << Math.min(16, normalized - 1);
        return Duration.ofSeconds(Math.min(300L, seconds));
    }

    /** worker 只有同时持有当前 generation、token 和未过期租约时才有写回资格。 */
    public boolean ownsLease(long expectedGeneration, String expectedToken, Instant now) {
        return generation == expectedGeneration
                && expectedToken != null
                && expectedToken.equals(leaseToken)
                && leaseUntil != null
                && now != null
                && now.isBefore(leaseUntil);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
