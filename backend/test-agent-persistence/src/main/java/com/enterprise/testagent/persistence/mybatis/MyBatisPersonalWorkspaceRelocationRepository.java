package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocation;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationCandidate;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 个人工作区跨服务器搬迁端口的 MyBatis XML 实现。 */
@Repository
public class MyBatisPersonalWorkspaceRelocationRepository
        implements PersonalWorkspaceRelocationRepository {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private final PersonalWorkspaceRelocationMapper mapper;

    public MyBatisPersonalWorkspaceRelocationRepository(PersonalWorkspaceRelocationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<PersonalWorkspaceRelocationCandidate> findMismatches(String sourceLinuxServerId, int limit) {
        requireLimit(limit);
        return mapper.findMismatches(sourceLinuxServerId, limit).stream().map(this::toCandidate).toList();
    }

    @Override
    public void discover(
            PersonalWorkspaceRelocationCandidate candidate,
            String relocationId,
            String traceId,
            Instant now) {
        mapper.discover(
                candidate.personalWorkspaceId().value(),
                candidate.runtimeWorkspaceId().value(),
                candidate.sourceLinuxServerId(),
                candidate.targetLinuxServerId(),
                relocationId,
                traceId,
                now);
    }

    @Override
    public List<PersonalWorkspaceRelocation> findClaimable(
            String sourceLinuxServerId, Instant now, int limit) {
        requireLimit(limit);
        return mapper.findClaimable(sourceLinuxServerId, now, limit).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<PersonalWorkspaceRelocation> claim(
            String relocationId,
            String leaseOwner,
            Instant leaseUntil,
            Instant now) {
        if (mapper.claim(relocationId, leaseOwner, leaseUntil, now) != 1) {
            return Optional.empty();
        }
        return findByRelocationId(relocationId);
    }

    @Override
    public Optional<PersonalWorkspaceRelocation> findByRelocationId(String relocationId) {
        return Optional.ofNullable(mapper.findByRelocationId(relocationId)).map(this::toDomain);
    }

    @Override
    public boolean markTransferring(
            String relocationId,
            String leaseOwner,
            String snapshotSha256,
            long archiveSizeBytes,
            Instant now) {
        requireSnapshot(snapshotSha256, archiveSizeBytes);
        return mapper.markTransferring(
                relocationId, leaseOwner, snapshotSha256, archiveSizeBytes, now) == 1;
    }

    @Override
    public boolean markApplying(
            String relocationId,
            String snapshotSha256,
            long archiveSizeBytes,
            Instant now) {
        requireSnapshot(snapshotSha256, archiveSizeBytes);
        return mapper.markApplying(relocationId, snapshotSha256, archiveSizeBytes, now) == 1;
    }

    @Override
    @Transactional
    public boolean completeTarget(
            String relocationId,
            String sourceLinuxServerId,
            String targetLinuxServerId,
            String targetRepoRootPath,
            String targetWorkspaceRootPath,
            String baseCommit,
            String traceId,
            Instant now) {
        if (mapper.lockTargetCompletion(relocationId, sourceLinuxServerId, targetLinuxServerId) == null) {
            return false;
        }
        requireSingleUpdate(mapper.updateRuntimeWorkspaceTarget(
                relocationId, sourceLinuxServerId, targetLinuxServerId, targetWorkspaceRootPath, traceId, now));
        requireSingleUpdate(mapper.updatePersonalWorkspaceTarget(
                relocationId, targetRepoRootPath, targetWorkspaceRootPath, baseCommit, now));
        requireSingleUpdate(mapper.markTargetComplete(relocationId, now));
        return true;
    }

    @Override
    public boolean reschedule(
            String relocationId,
            String leaseOwner,
            int attemptCount,
            Instant nextRetryAt,
            String safeErrorCode,
            String safeErrorMessage,
            Instant now) {
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        return mapper.reschedule(
                relocationId,
                leaseOwner,
                attemptCount,
                nextRetryAt,
                safeErrorCode,
                safeErrorMessage,
                now) == 1;
    }

    @Override
    public boolean completeCleanup(String relocationId, String leaseOwner, Instant now) {
        return mapper.completeCleanup(relocationId, leaseOwner, now) == 1;
    }

    private PersonalWorkspaceRelocationCandidate toCandidate(PersonalWorkspaceRelocationCandidateRow row) {
        return new PersonalWorkspaceRelocationCandidate(
                new PersonalWorkspaceId(row.personalWorkspaceId()),
                new ApplicationWorkspaceVersionId(row.appWorkspaceVersionId()),
                new UserId(row.userId()),
                new WorkspaceId(row.runtimeWorkspaceId()),
                row.sourceLinuxServerId(),
                row.targetLinuxServerId());
    }

    private PersonalWorkspaceRelocation toDomain(PersonalWorkspaceRelocationRow row) {
        return new PersonalWorkspaceRelocation(
                row.relocationId(),
                new PersonalWorkspaceId(row.personalWorkspaceId()),
                new ApplicationWorkspaceVersionId(row.appWorkspaceVersionId()),
                new UserId(row.userId()),
                new WorkspaceId(row.runtimeWorkspaceId()),
                row.sourceLinuxServerId(),
                row.targetLinuxServerId(),
                row.branch(),
                row.sourceRepoRootPath(),
                row.sourceWorkspaceRootPath(),
                PersonalWorkspaceRelocationStatus.valueOf(row.status()),
                row.attemptCount(),
                row.leaseOwner(),
                row.leaseUntil(),
                row.snapshotSha256(),
                row.archiveSizeBytes(),
                row.traceId(),
                row.createdAt(),
                row.updatedAt());
    }

    private void requireLimit(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
    }

    private void requireSnapshot(String sha256, long size) {
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("snapshotSha256 must be lowercase SHA-256");
        }
        if (size < 0) {
            throw new IllegalArgumentException("archiveSizeBytes must not be negative");
        }
    }

    private void requireSingleUpdate(int count) {
        if (count != 1) {
            throw new IllegalStateException("personal workspace relocation transaction lost its fenced row");
        }
    }
}
