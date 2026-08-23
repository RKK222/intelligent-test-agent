package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 公共客户端能力包 MyBatis 仓储；发布制品不可变，实例状态允许幂等覆盖。 */
@Repository
public class MyBatisLocalClientPublicCapabilityRepository implements LocalClientPublicCapabilityRepository {

    private final LocalClientPublicCapabilityMapper mapper;

    public MyBatisLocalClientPublicCapabilityRepository(LocalClientPublicCapabilityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertRelease(LocalClientPublicCapabilityModels.Release release) {
        if (mapper.insertRelease(releaseRow(release)) != 1) {
            throw new IllegalStateException("public capability release was not inserted");
        }
    }

    @Override
    public Optional<LocalClientPublicCapabilityModels.Release> findReleaseByDigest(String bundleDigest) {
        return Optional.ofNullable(mapper.findReleaseByDigest(bundleDigest)).map(this::release);
    }

    @Override
    public Optional<LocalClientPublicCapabilityModels.Release> findReleaseBySourceCommit(String sourceCommit) {
        return Optional.ofNullable(mapper.findReleaseBySourceCommit(sourceCommit)).map(this::release);
    }

    @Override
    public Optional<LocalClientPublicCapabilityModels.Release> findLatestRelease() {
        return Optional.ofNullable(mapper.findLatestRelease()).map(this::release);
    }

    @Override
    public Optional<LocalClientPublicCapabilityModels.Release> findLatestAvailableRelease() {
        return Optional.ofNullable(mapper.findLatestAvailableRelease()).map(this::release);
    }

    @Override
    public Optional<LocalClientPublicCapabilityModels.InstanceState> findInstanceState(LocalClientInstanceId instanceId) {
        return Optional.ofNullable(mapper.findInstanceState(instanceId.value())).map(this::state);
    }

    @Override
    public List<LocalClientPublicCapabilityModels.InstanceState> findUpdateAvailableStates(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("state limit must be between 1 and 1000");
        }
        return mapper.findUpdateAvailableStates(limit).stream().map(this::state).toList();
    }

    @Override
    @Transactional
    public void saveInstanceState(LocalClientPublicCapabilityModels.InstanceState state) {
        LocalClientPublicCapabilityStateRow row = stateRow(state);
        if (mapper.upsertInstanceState(row) != 1 || mapper.projectInstanceState(row) != 1) {
            throw new IllegalStateException("public capability instance state was not saved");
        }
    }

    @Override
    public void markUpdateAvailableForCapableInstances(
            String sourceCommit,
            String bundleDigest,
            Instant observedAt,
            String capabilityName) {
        mapper.markUpdateAvailableForCapableInstances(sourceCommit, bundleDigest, observedAt, capabilityName);
    }

    @Override
    public void insertAttempt(LocalClientPublicCapabilityModels.Attempt attempt) {
        if (mapper.insertAttempt(attemptRow(attempt)) != 1) {
            throw new IllegalStateException("public capability attempt was not inserted");
        }
    }

    @Override
    public Optional<LocalClientPublicCapabilityModels.Attempt> findAttempt(String commandId) {
        return Optional.ofNullable(mapper.findAttempt(commandId)).map(this::attempt);
    }

    @Override
    public Optional<LocalClientPublicCapabilityModels.Attempt> findAttempt(
            LocalClientInstanceId clientInstanceId,
            String targetDigest) {
        return Optional.ofNullable(mapper.findAttemptByTarget(clientInstanceId.value(), targetDigest))
                .map(this::attempt);
    }

    @Override
    public List<LocalClientPublicCapabilityModels.Attempt> findDispatchableAttempts(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("dispatch limit must be between 1 and 1000");
        }
        return mapper.findDispatchableAttempts(limit).stream().map(this::attempt).toList();
    }

    @Override
    public boolean bindAttemptGeneration(
            String commandId,
            long expectedGeneration,
            long targetGeneration,
            Instant observedAt) {
        if (targetGeneration < 1) {
            throw new IllegalArgumentException("target generation must be positive");
        }
        return mapper.bindAttemptGeneration(commandId, expectedGeneration, targetGeneration, observedAt) == 1;
    }

    @Override
    public boolean transitionAttempt(
            String commandId,
            String expectedStatus,
            String targetStatus,
            String errorCode,
            Instant observedAt) {
        LocalClientPublicCapabilityModels.AttemptStatus target =
                LocalClientPublicCapabilityModels.AttemptStatus.valueOf(targetStatus);
        return mapper.transitionAttempt(
                commandId,
                LocalClientPublicCapabilityModels.AttemptStatus.valueOf(expectedStatus).name(),
                target.name(),
                target.terminal(),
                errorCode,
                observedAt) == 1;
    }

    private static LocalClientPublicCapabilityReleaseRow releaseRow(
            LocalClientPublicCapabilityModels.Release release) {
        return new LocalClientPublicCapabilityReleaseRow(
                release.sourceCommit(), release.bundleDigest(), release.artifactSha256(),
                release.compatibility().name(), release.errorCode(), release.manifestJson(),
                release.changeSummaryJson(), release.counts().agents(), release.counts().skills(),
                release.counts().tools(), release.requiresRestart(), release.artifact(), release.compressedSize(),
                release.uncompressedSize(), release.fileCount(), release.createdAt());
    }

    private LocalClientPublicCapabilityModels.Release release(LocalClientPublicCapabilityReleaseRow row) {
        return new LocalClientPublicCapabilityModels.Release(
                row.sourceCommit(), row.bundleDigest(), row.artifactSha256(),
                LocalClientPublicCapabilityModels.Compatibility.valueOf(row.compatibility()), row.errorCode(),
                row.manifestJson(), row.changeSummaryJson(),
                new LocalClientPublicCapabilityModels.Counts(row.agentCount(), row.skillCount(), row.toolCount()),
                row.requiresRestart(), row.artifact(), row.compressedSize(), row.uncompressedSize(),
                row.fileCount(), row.createdAt());
    }

    private static LocalClientPublicCapabilityStateRow stateRow(
            LocalClientPublicCapabilityModels.InstanceState state) {
        return new LocalClientPublicCapabilityStateRow(
                state.clientInstanceId().value(), state.activeCommit(), state.activeDigest(), state.pendingCommit(),
                state.pendingDigest(), state.status().name(), state.errorCode(), state.reportedAt(), state.updatedAt());
    }

    private LocalClientPublicCapabilityModels.InstanceState state(LocalClientPublicCapabilityStateRow row) {
        return new LocalClientPublicCapabilityModels.InstanceState(
                new LocalClientInstanceId(row.clientInstanceId()), row.activeCommit(), row.activeDigest(),
                row.pendingCommit(), row.pendingDigest(),
                LocalClientPublicCapabilityModels.InstanceStatus.valueOf(row.status()), row.errorCode(),
                row.reportedAt(), row.updatedAt());
    }

    private static LocalClientPublicCapabilityAttemptRow attemptRow(
            LocalClientPublicCapabilityModels.Attempt attempt) {
        return new LocalClientPublicCapabilityAttemptRow(
                attempt.commandId(), attempt.clientInstanceId().value(), attempt.userId().value(),
                attempt.connectionGeneration(), attempt.targetCommit(), attempt.targetDigest(), attempt.status().name(),
                attempt.errorCode(), attempt.createdAt(), attempt.updatedAt(), attempt.completedAt());
    }

    private LocalClientPublicCapabilityModels.Attempt attempt(LocalClientPublicCapabilityAttemptRow row) {
        return new LocalClientPublicCapabilityModels.Attempt(
                row.commandId(), new LocalClientInstanceId(row.clientInstanceId()), new UserId(row.userId()),
                row.connectionGeneration(), row.targetCommit(), row.targetDigest(),
                LocalClientPublicCapabilityModels.AttemptStatus.valueOf(row.status()), row.errorCode(),
                row.createdAt(), row.updatedAt(), row.completedAt());
    }
}
