package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import com.enterprise.testagent.common.localclient.LocalClientUpdateDirection;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 本地客户端发布、策略、rollout 与实例 attempt 的稳定领域数据契约。 */
public final class LocalClientVersionModels {

    private LocalClientVersionModels() {
    }

    public static EffectivePolicy resolveEffectivePolicy(GlobalPolicy global, UserPolicy user) {
        if (user != null && user.targetVersion() != null) {
            return new EffectivePolicy(user.targetVersion(), user.revision(), "USER");
        }
        long revision = Math.max(global == null ? 0 : global.revision(), user == null ? 0 : user.revision());
        return new EffectivePolicy(global == null ? null : global.targetVersion(), revision,
                global == null ? "NONE" : "GLOBAL");
    }

    public record EffectivePolicy(String targetVersion, long policyRevision, String source) {
    }

    public record Artifact(String kind, String url, long size, String sha256, String signature) {
        public Artifact {
            kind = required(kind, "kind", 32);
            url = required(url, "url", 2048);
            if (size < 1) {
                throw new IllegalArgumentException("artifact size must be positive");
            }
            sha256 = digest(sha256, "sha256");
            signature = required(signature, "signature", 4096);
        }
    }

    public record Release(
            String version,
            String platform,
            String architecture,
            int launcherVersionMin,
            int launcherVersionMax,
            String protocolVersion,
            String manifestUrl,
            String manifestSha256,
            String manifestSignature,
            boolean compatible,
            Instant publishedAt,
            Instant syncedAt,
            List<Artifact> artifacts) {
        public Release {
            version = LocalClientReleaseVersion.parse(version).value();
            platform = required(platform, "platform", 64);
            architecture = required(architecture, "architecture", 64);
            if (launcherVersionMin < 1 || launcherVersionMax < launcherVersionMin) {
                throw new IllegalArgumentException("launcher version range is invalid");
            }
            protocolVersion = required(protocolVersion, "protocolVersion", 128);
            manifestUrl = required(manifestUrl, "manifestUrl", 2048);
            manifestSha256 = digest(manifestSha256, "manifestSha256");
            manifestSignature = required(manifestSignature, "manifestSignature", 4096);
            publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
            syncedAt = Objects.requireNonNull(syncedAt, "syncedAt must not be null");
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
            if (artifacts.isEmpty()) {
                throw new IllegalArgumentException("release artifacts must not be empty");
            }
        }
    }

    public record GlobalPolicy(String targetVersion, long revision, UserId updatedBy, Instant updatedAt) {
        public GlobalPolicy {
            targetVersion = optionalVersion(targetVersion);
            positiveRevision(revision);
            Objects.requireNonNull(updatedBy, "updatedBy must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        }
    }

    /** targetVersion=null 是清除覆盖后的 tombstone，用于保留策略修订 fencing。 */
    public record UserPolicy(
            UserId userId,
            String targetVersion,
            long revision,
            UserId updatedBy,
            Instant updatedAt) {
        public UserPolicy {
            Objects.requireNonNull(userId, "userId must not be null");
            targetVersion = optionalVersion(targetVersion);
            positiveRevision(revision);
            Objects.requireNonNull(updatedBy, "updatedBy must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        }
    }

    public record PolicyAudit(
            String auditId,
            String scope,
            UserId userId,
            String previousTargetVersion,
            String targetVersion,
            long revision,
            String action,
            UserId actorUserId,
            Instant createdAt) {
        public PolicyAudit {
            auditId = required(auditId, "auditId", 128);
            scope = required(scope, "scope", 16);
            previousTargetVersion = optionalVersion(previousTargetVersion);
            targetVersion = optionalVersion(targetVersion);
            positiveRevision(revision);
            action = required(action, "action", 32);
            Objects.requireNonNull(actorUserId, "actorUserId must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }

    public enum RolloutScope {
        ALL_ONLINE,
        USER
    }

    public enum RolloutStatus {
        RUNNING,
        COMPLETED,
        PARTIAL_FAILED
    }

    public record Rollout(
            String rolloutId,
            RolloutScope scope,
            UserId requestedUserId,
            RolloutStatus status,
            UserId createdBy,
            Instant createdAt,
            Instant completedAt) {
        public Rollout {
            rolloutId = required(rolloutId, "rolloutId", 128);
            Objects.requireNonNull(scope, "scope must not be null");
            if (scope == RolloutScope.USER && requestedUserId == null) {
                throw new IllegalArgumentException("USER rollout requires requestedUserId");
            }
            if (scope == RolloutScope.ALL_ONLINE && requestedUserId != null) {
                throw new IllegalArgumentException("ALL_ONLINE rollout must not have requestedUserId");
            }
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdBy, "createdBy must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (status == RolloutStatus.RUNNING && completedAt != null) {
                throw new IllegalArgumentException("running rollout must not have completedAt");
            }
            if (status != RolloutStatus.RUNNING && completedAt == null) {
                throw new IllegalArgumentException("terminal rollout requires completedAt");
            }
        }
    }

    public enum AttemptStatus {
        PENDING,
        SENT,
        PREPARING,
        PREPARED,
        APPLYING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        AUTO_ROLLED_BACK;

        public boolean terminal() {
            return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == AUTO_ROLLED_BACK;
        }
    }

    public record Attempt(
            String commandId,
            String rolloutId,
            LocalClientInstanceId clientInstanceId,
            UserId userId,
            long connectionGeneration,
            long policyRevision,
            String currentVersion,
            String targetVersion,
            LocalClientUpdateDirection direction,
            AttemptStatus status,
            String releaseDigest,
            String errorCode,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {
        public Attempt {
            commandId = required(commandId, "commandId", 128);
            rolloutId = required(rolloutId, "rolloutId", 128);
            Objects.requireNonNull(clientInstanceId, "clientInstanceId must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
            if (connectionGeneration < 1) {
                throw new IllegalArgumentException("connectionGeneration must be positive");
            }
            positiveRevision(policyRevision);
            currentVersion = required(currentVersion, "currentVersion", 64);
            targetVersion = LocalClientReleaseVersion.parse(targetVersion).value();
            Objects.requireNonNull(direction, "direction must not be null");
            if (direction == LocalClientUpdateDirection.SAME) {
                throw new IllegalArgumentException("SAME attempt must not be persisted");
            }
            Objects.requireNonNull(status, "status must not be null");
            releaseDigest = optionalDigest(releaseDigest);
            errorCode = optional(errorCode, 128);
            createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
            if (status.terminal() && completedAt == null) {
                throw new IllegalArgumentException("terminal attempt requires completedAt");
            }
            if (!status.terminal() && completedAt != null) {
                throw new IllegalArgumentException("non-terminal attempt must not have completedAt");
            }
        }
    }

    private static String optionalVersion(String value) {
        return value == null ? null : LocalClientReleaseVersion.parse(value).value();
    }

    private static String digest(String value, String field) {
        String normalized = required(value, field, 64).toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest");
        }
        return normalized;
    }

    private static String optionalDigest(String value) {
        return value == null ? null : digest(value, "releaseDigest");
    }

    private static void positiveRevision(long revision) {
        if (revision < 1) {
            throw new IllegalArgumentException("policy revision must be positive");
        }
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
