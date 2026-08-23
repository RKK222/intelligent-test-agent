package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 公共 Agent/Skill/Tool 客户端能力包、实例状态和安装命令的稳定领域契约。 */
public final class LocalClientPublicCapabilityModels {

    private LocalClientPublicCapabilityModels() {
    }

    public enum Compatibility {
        AVAILABLE,
        SERVER_ONLY
    }

    public enum InstanceStatus {
        CURRENT,
        UPDATE_AVAILABLE,
        PENDING,
        DOWNLOADING,
        APPLYING,
        SUCCEEDED,
        FAILED,
        ROLLED_BACK
    }

    public enum AttemptStatus {
        PENDING,
        SENT,
        DOWNLOADING,
        APPLYING,
        SUCCEEDED,
        FAILED,
        ROLLED_BACK;

        public boolean terminal() {
            return this == SUCCEEDED || this == FAILED || this == ROLLED_BACK;
        }
    }

    public record Counts(int agents, int skills, int tools) {
        public Counts {
            if (agents < 0 || skills < 0 || tools < 0) {
                throw new IllegalArgumentException("capability counts must not be negative");
            }
        }
    }

    /** artifact 为 gzip 字节；SERVER_ONLY 版本没有客户端制品。 */
    public record Release(
            String sourceCommit,
            String bundleDigest,
            String artifactSha256,
            Compatibility compatibility,
            String errorCode,
            String manifestJson,
            String changeSummaryJson,
            Counts counts,
            boolean requiresRestart,
            byte[] artifact,
            long compressedSize,
            long uncompressedSize,
            int fileCount,
            Instant createdAt) {
        public Release {
            sourceCommit = requiredHex(sourceCommit, "sourceCommit", 40, 64);
            bundleDigest = digest(bundleDigest, "bundleDigest");
            artifactSha256 = compatibility == Compatibility.AVAILABLE
                    ? digest(artifactSha256, "artifactSha256") : null;
            Objects.requireNonNull(compatibility, "compatibility must not be null");
            errorCode = optional(errorCode, 128);
            manifestJson = required(manifestJson, "manifestJson", 4 * 1024 * 1024);
            changeSummaryJson = required(changeSummaryJson, "changeSummaryJson", 1024 * 1024);
            Objects.requireNonNull(counts, "counts must not be null");
            createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
            artifact = artifact == null ? null : artifact.clone();
            if (compatibility == Compatibility.AVAILABLE
                    && (artifact == null || compressedSize != artifact.length || compressedSize < 1)) {
                throw new IllegalArgumentException("available capability release requires matching artifact bytes");
            }
            if (compatibility == Compatibility.SERVER_ONLY && artifact != null) {
                throw new IllegalArgumentException("server-only capability release must not contain artifact");
            }
            if (uncompressedSize < 0 || fileCount < 0) {
                throw new IllegalArgumentException("capability release sizes are invalid");
            }
        }

        @Override
        public byte[] artifact() {
            return artifact == null ? null : artifact.clone();
        }
    }

    public record InstanceState(
            LocalClientInstanceId clientInstanceId,
            String activeCommit,
            String activeDigest,
            String pendingCommit,
            String pendingDigest,
            InstanceStatus status,
            String errorCode,
            Instant reportedAt,
            Instant updatedAt) {
        public InstanceState {
            Objects.requireNonNull(clientInstanceId, "clientInstanceId must not be null");
            activeCommit = optionalHex(activeCommit);
            activeDigest = optionalDigest(activeDigest);
            pendingCommit = optionalHex(pendingCommit);
            pendingDigest = optionalDigest(pendingDigest);
            Objects.requireNonNull(status, "status must not be null");
            errorCode = optional(errorCode, 128);
            reportedAt = Objects.requireNonNull(reportedAt, "reportedAt must not be null");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        }
    }

    public record Attempt(
            String commandId,
            LocalClientInstanceId clientInstanceId,
            UserId userId,
            long connectionGeneration,
            String targetCommit,
            String targetDigest,
            AttemptStatus status,
            String errorCode,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {
        public Attempt {
            commandId = required(commandId, "commandId", 128);
            Objects.requireNonNull(clientInstanceId, "clientInstanceId must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
            if (connectionGeneration < 0) {
                throw new IllegalArgumentException("connectionGeneration must not be negative");
            }
            targetCommit = requiredHex(targetCommit, "targetCommit", 40, 64);
            targetDigest = digest(targetDigest, "targetDigest");
            Objects.requireNonNull(status, "status must not be null");
            errorCode = optional(errorCode, 128);
            createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
            if (status.terminal() != (completedAt != null)) {
                throw new IllegalArgumentException("capability attempt terminal timestamp is inconsistent");
            }
        }
    }

    private static String optionalHex(String value) {
        return value == null ? null : requiredHex(value, "commit", 40, 64);
    }

    private static String optionalDigest(String value) {
        return value == null ? null : digest(value, "digest");
    }

    private static String requiredHex(String value, String field, int min, int max) {
        String normalized = required(value, field, max).toLowerCase();
        if (normalized.length() < min || !normalized.matches("[0-9a-f]+")) {
            throw new IllegalArgumentException(field + " must be a hexadecimal commit id");
        }
        return normalized;
    }

    private static String digest(String value, String field) {
        String normalized = required(value, field, 64).toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest");
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("optional value is too long");
        }
        return value.trim();
    }
}
