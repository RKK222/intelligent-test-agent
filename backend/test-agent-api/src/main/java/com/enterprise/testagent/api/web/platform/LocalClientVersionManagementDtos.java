package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import java.time.Instant;
import java.util.List;

/** 本地客户端版本管理 HTTP DTO；签名原文不返回浏览器。 */
final class LocalClientVersionManagementDtos {

    private LocalClientVersionManagementDtos() {
    }

    record SetTargetVersionRequest(String targetVersion) {
    }

    record CreateRolloutRequest(String scope, String userId) {
    }

    record UserUpdateRequest(String notificationId, String expectedTargetVersion) {
    }

    record SyncView(int synced, int unchanged, int discovered) {
    }

    record ArtifactView(String kind, String url, long size, String sha256) {
    }

    record ReleaseView(
            String version,
            String platform,
            String architecture,
            int launcherVersionMin,
            int launcherVersionMax,
            String protocolVersion,
            String manifestSha256,
            boolean compatible,
            Instant publishedAt,
            Instant syncedAt,
            List<ArtifactView> artifacts) {
    }

    record GlobalPolicyView(String targetVersion, long revision, String updatedBy, Instant updatedAt) {
    }

    record UserPolicyView(
            String userId,
            String targetVersion,
            long revision,
            String updatedBy,
            Instant updatedAt) {
    }

    record RolloutView(
            String rolloutId,
            String scope,
            String requestedUserId,
            String status,
            String createdBy,
            Instant createdAt,
            Instant completedAt,
            Integer attemptCount) {
    }

    record AttemptView(
            String commandId,
            String rolloutId,
            String clientInstanceId,
            String userId,
            long connectionGeneration,
            long policyRevision,
            String currentVersion,
            String targetVersion,
            String direction,
            String status,
            String releaseDigest,
            String errorCode,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {
    }

    static ReleaseView release(LocalClientVersionModels.Release release) {
        return new ReleaseView(
                release.version(),
                release.platform(),
                release.architecture(),
                release.launcherVersionMin(),
                release.launcherVersionMax(),
                release.protocolVersion(),
                release.manifestSha256(),
                release.compatible(),
                release.publishedAt(),
                release.syncedAt(),
                release.artifacts().stream()
                        .map(artifact -> new ArtifactView(
                                artifact.kind(), artifact.url(), artifact.size(), artifact.sha256()))
                        .toList());
    }

    static GlobalPolicyView globalPolicy(LocalClientVersionModels.GlobalPolicy policy) {
        return policy == null
                ? new GlobalPolicyView(null, 0, null, null)
                : new GlobalPolicyView(
                        policy.targetVersion(),
                        policy.revision(),
                        policy.updatedBy().value(),
                        policy.updatedAt());
    }

    static UserPolicyView userPolicy(LocalClientVersionModels.UserPolicy policy) {
        return new UserPolicyView(
                policy.userId().value(),
                policy.targetVersion(),
                policy.revision(),
                policy.updatedBy().value(),
                policy.updatedAt());
    }

    static RolloutView rollout(LocalClientVersionModels.Rollout rollout, Integer attemptCount) {
        return new RolloutView(
                rollout.rolloutId(),
                rollout.scope().name(),
                rollout.requestedUserId() == null ? null : rollout.requestedUserId().value(),
                rollout.status().name(),
                rollout.createdBy().value(),
                rollout.createdAt(),
                rollout.completedAt(),
                attemptCount);
    }

    static AttemptView attempt(LocalClientVersionModels.Attempt attempt) {
        return new AttemptView(
                attempt.commandId(),
                attempt.rolloutId(),
                attempt.clientInstanceId().value(),
                attempt.userId().value(),
                attempt.connectionGeneration(),
                attempt.policyRevision(),
                attempt.currentVersion(),
                attempt.targetVersion(),
                attempt.direction().name(),
                attempt.status().name(),
                attempt.releaseDigest(),
                attempt.errorCode(),
                attempt.createdAt(),
                attempt.updatedAt(),
                attempt.completedAt());
    }
}
