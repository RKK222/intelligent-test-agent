package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.localclient.LocalClientUpdateDirection;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 客户端版本管理 MyBatis 仓储；发布插入保持不可变，不使用覆盖式 upsert。 */
@Repository
public class MyBatisLocalClientVersionRepository implements LocalClientVersionRepository {

    private final LocalClientVersionMapper mapper;

    public MyBatisLocalClientVersionRepository(LocalClientVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<LocalClientVersionModels.Release> findRelease(String version) {
        return Optional.ofNullable(mapper.findRelease(version)).map(this::release);
    }

    @Override
    public List<LocalClientVersionModels.Release> findReleases() {
        return mapper.findReleases().stream().map(this::release).toList();
    }

    @Override
    public void insertRelease(LocalClientVersionModels.Release release) {
        LocalClientReleaseRow row = new LocalClientReleaseRow(
                release.version(),
                release.platform(),
                release.architecture(),
                release.launcherVersionMin(),
                release.launcherVersionMax(),
                release.protocolVersion(),
                release.manifestUrl(),
                release.manifestSha256(),
                release.manifestSignature(),
                release.compatible(),
                release.publishedAt(),
                release.syncedAt());
        if (mapper.insertRelease(row) != 1) {
            throw new IllegalStateException("local client release was not inserted");
        }
        for (LocalClientVersionModels.Artifact artifact : release.artifacts()) {
            if (mapper.insertReleaseArtifact(new LocalClientReleaseArtifactRow(
                    release.version(),
                    artifact.kind(),
                    artifact.url(),
                    artifact.size(),
                    artifact.sha256(),
                    artifact.signature())) != 1) {
                throw new IllegalStateException("local client release artifact was not inserted");
            }
        }
    }

    @Override
    public long nextPolicyRevision() {
        Long revision = mapper.nextPolicyRevision();
        if (revision == null || revision < 1) {
            throw new IllegalStateException("local client policy revision sequence returned invalid value");
        }
        return revision;
    }

    @Override
    public Optional<LocalClientVersionModels.GlobalPolicy> findGlobalPolicy() {
        return Optional.ofNullable(mapper.findGlobalPolicy()).map(row -> new LocalClientVersionModels.GlobalPolicy(
                row.targetVersion(), row.revision(), new UserId(row.updatedBy()), row.updatedAt()));
    }

    @Override
    public void saveGlobalPolicy(LocalClientVersionModels.GlobalPolicy policy) {
        if (mapper.upsertGlobalPolicy(new LocalClientGlobalPolicyRow(
                policy.targetVersion(), policy.revision(), policy.updatedBy().value(), policy.updatedAt())) != 1) {
            throw new IllegalStateException("stale local client global policy revision");
        }
    }

    @Override
    public Optional<LocalClientVersionModels.UserPolicy> findUserPolicy(UserId userId) {
        return Optional.ofNullable(mapper.findUserPolicy(userId.value())).map(this::userPolicy);
    }

    @Override
    public List<LocalClientVersionModels.UserPolicy> findUserPolicies() {
        return mapper.findUserPolicies().stream().map(this::userPolicy).toList();
    }

    @Override
    public void saveUserPolicy(LocalClientVersionModels.UserPolicy policy) {
        if (mapper.upsertUserPolicy(new LocalClientUserPolicyRow(
                policy.userId().value(),
                policy.targetVersion(),
                policy.revision(),
                policy.updatedBy().value(),
                policy.updatedAt())) != 1) {
            throw new IllegalStateException("stale local client user policy revision");
        }
    }

    @Override
    public void savePolicyAudit(LocalClientVersionModels.PolicyAudit audit) {
        if (mapper.insertPolicyAudit(
                audit.auditId(),
                audit.scope(),
                audit.userId() == null ? null : audit.userId().value(),
                audit.previousTargetVersion(),
                audit.targetVersion(),
                audit.revision(),
                audit.action(),
                audit.actorUserId().value(),
                audit.createdAt()) != 1) {
            throw new IllegalStateException("local client policy audit was not inserted");
        }
    }

    @Override
    public void insertRollout(LocalClientVersionModels.Rollout rollout) {
        if (mapper.insertRollout(new LocalClientRolloutRow(
                rollout.rolloutId(),
                rollout.scope().name(),
                rollout.requestedUserId() == null ? null : rollout.requestedUserId().value(),
                rollout.status().name(),
                rollout.createdBy().value(),
                rollout.createdAt(),
                rollout.completedAt())) != 1) {
            throw new IllegalStateException("local client rollout was not inserted");
        }
    }

    @Override
    public List<LocalClientVersionModels.Rollout> findRollouts() {
        return mapper.findRollouts().stream().map(this::rollout).toList();
    }

    @Override
    public Optional<LocalClientVersionModels.Rollout> findRollout(String rolloutId) {
        return Optional.ofNullable(mapper.findRollout(rolloutId)).map(this::rollout);
    }

    @Override
    public Optional<LocalClientVersionModels.Rollout> findRolloutForUpdate(String rolloutId) {
        return Optional.ofNullable(mapper.findRolloutForUpdate(rolloutId)).map(this::rollout);
    }

    @Override
    public boolean completeRollout(String rolloutId, String status, Instant completedAt) {
        LocalClientVersionModels.RolloutStatus target = LocalClientVersionModels.RolloutStatus.valueOf(status);
        if (target == LocalClientVersionModels.RolloutStatus.RUNNING) {
            throw new IllegalArgumentException("completed rollout status must be terminal");
        }
        return mapper.completeRollout(rolloutId, target.name(), completedAt) == 1;
    }

    @Override
    public void insertAttempts(List<LocalClientVersionModels.Attempt> attempts) {
        for (LocalClientVersionModels.Attempt attempt : attempts) {
            if (mapper.insertAttempt(attemptRow(attempt)) != 1) {
                throw new IllegalStateException("local client update attempt was not inserted");
            }
        }
    }

    @Override
    public List<LocalClientVersionModels.Attempt> findAttemptsByRollout(String rolloutId) {
        return mapper.findAttemptsByRollout(rolloutId).stream().map(this::attempt).toList();
    }

    @Override
    public Optional<LocalClientVersionModels.Attempt> findAttempt(String commandId) {
        return Optional.ofNullable(mapper.findAttempt(commandId)).map(this::attempt);
    }

    @Override
    public List<LocalClientVersionModels.Attempt> findDispatchableAttempts(int limit, int offset, Instant now) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("dispatchable attempt limit must be between 1 and 1000");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("dispatchable attempt offset must not be negative");
        }
        Objects.requireNonNull(now, "now must not be null");
        return mapper.findDispatchableAttempts(limit, offset, now, now.minus(java.time.Duration.ofMinutes(30)))
                .stream().map(this::attempt).toList();
    }

    @Override
    public boolean transitionAttempt(
            String commandId,
            String expectedStatus,
            String status,
            String releaseDigest,
            String errorCode,
            Instant observedAt) {
        LocalClientVersionModels.AttemptStatus targetStatus = LocalClientVersionModels.AttemptStatus.valueOf(status);
        return mapper.transitionAttempt(
                commandId,
                LocalClientVersionModels.AttemptStatus.valueOf(expectedStatus).name(),
                targetStatus.name(),
                targetStatus.terminal(),
                releaseDigest,
                errorCode,
                observedAt) == 1;
    }

    private LocalClientVersionModels.Release release(LocalClientReleaseRow row) {
        List<LocalClientVersionModels.Artifact> artifacts = mapper.findReleaseArtifacts(row.version()).stream()
                .map(artifact -> new LocalClientVersionModels.Artifact(
                        artifact.artifactKind(),
                        artifact.artifactUrl(),
                        artifact.artifactSize(),
                        artifact.artifactSha256(),
                        artifact.artifactSignature()))
                .toList();
        return new LocalClientVersionModels.Release(
                row.version(),
                row.platform(),
                row.architecture(),
                row.launcherVersionMin(),
                row.launcherVersionMax(),
                row.protocolVersion(),
                row.manifestUrl(),
                row.manifestSha256(),
                row.manifestSignature(),
                row.compatible(),
                row.publishedAt(),
                row.syncedAt(),
                artifacts);
    }

    private LocalClientVersionModels.UserPolicy userPolicy(LocalClientUserPolicyRow row) {
        return new LocalClientVersionModels.UserPolicy(
                new UserId(row.userId()),
                row.targetVersion(),
                row.revision(),
                new UserId(row.updatedBy()),
                row.updatedAt());
    }

    private LocalClientVersionModels.Rollout rollout(LocalClientRolloutRow row) {
        return new LocalClientVersionModels.Rollout(
                row.rolloutId(),
                LocalClientVersionModels.RolloutScope.valueOf(row.rolloutScope()),
                row.requestedUserId() == null ? null : new UserId(row.requestedUserId()),
                LocalClientVersionModels.RolloutStatus.valueOf(row.status()),
                new UserId(row.createdBy()),
                row.createdAt(),
                row.completedAt());
    }

    private LocalClientUpdateAttemptRow attemptRow(LocalClientVersionModels.Attempt attempt) {
        return new LocalClientUpdateAttemptRow(
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

    private LocalClientVersionModels.Attempt attempt(LocalClientUpdateAttemptRow row) {
        return new LocalClientVersionModels.Attempt(
                row.commandId(),
                row.rolloutId(),
                new LocalClientInstanceId(row.clientInstanceId()),
                new UserId(row.userId()),
                row.connectionGeneration(),
                row.policyRevision(),
                row.currentVersion(),
                row.targetVersion(),
                LocalClientUpdateDirection.valueOf(row.direction()),
                LocalClientVersionModels.AttemptStatus.valueOf(row.status()),
                row.releaseDigest(),
                row.errorCode(),
                row.createdAt(),
                row.updatedAt(),
                row.completedAt());
    }
}
