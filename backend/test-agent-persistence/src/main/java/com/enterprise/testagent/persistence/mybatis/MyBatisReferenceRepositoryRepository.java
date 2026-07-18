package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplica;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryOperationType;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryRepository;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryState;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 引用资产库仓储的 MyBatis XML 实现。 */
@Repository
public class MyBatisReferenceRepositoryRepository implements ReferenceRepositoryRepository {

    private final ReferenceRepositoryMapper mapper;

    public MyBatisReferenceRepositoryRepository(ReferenceRepositoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ReferenceRepositoryState> findState(CodeRepositoryId repositoryId) {
        return Optional.ofNullable(mapper.findState(repositoryId.value())).map(this::toState);
    }

    @Override
    public List<ReferenceRepositoryState> findStatesAfter(CodeRepositoryId afterExclusive, int limit) {
        String cursor = afterExclusive == null ? null : afterExclusive.value();
        return mapper.findStatesAfter(cursor, Math.max(1, Math.min(1000, limit))).stream()
                .map(this::toState)
                .toList();
    }

    @Override
    public List<ReferenceRepositoryReplica> findReplicas(CodeRepositoryId repositoryId) {
        return mapper.findReplicas(repositoryId.value()).stream().map(this::toReplica).toList();
    }

    @Override
    public Optional<ReferenceRepositoryState> initializeIfAbsent(ReferenceRepositoryState state) {
        try {
            if (mapper.initializeStateIfAbsent(toRow(state)) != 1) {
                return Optional.empty();
            }
            return findState(state.repositoryId());
        } catch (DuplicateKeyException concurrentWinner) {
            // H2 MERGE 的两个并发快照都可能进入 NOT MATCHED；唯一键冲突等价于 CAS 竞争失败。
            return Optional.empty();
        }
    }

    @Override
    public Optional<ReferenceRepositoryState> advanceGenerationIfCurrent(
            long expectedGeneration,
            String expectedOldBranch,
            ReferenceRepositoryState nextState) {
        if (mapper.advanceStateIfCurrent(expectedGeneration, expectedOldBranch, toRow(nextState)) != 1) {
            return Optional.empty();
        }
        return findState(nextState.repositoryId());
    }

    @Override
    @Transactional
    public void upsertTargets(
            CodeRepositoryId repositoryId,
            long generation,
            String branch,
            Collection<LinuxServerId> linuxServerIds,
            Instant now) {
        for (LinuxServerId linuxServerId : linuxServerIds) {
            mapper.upsertTarget(repositoryId.value(), linuxServerId.value(), generation, branch, now);
        }
    }

    @Override
    public int deferOfflineReplicas(
            CodeRepositoryId repositoryId,
            long generation,
            Collection<LinuxServerId> liveServerIds,
            Instant now) {
        List<String> liveIds = liveServerIds.stream().map(LinuxServerId::value).toList();
        return mapper.deferOfflineReplicas(repositoryId.value(), generation, liveIds, now);
    }

    @Override
    @Transactional
    public Optional<ReferenceRepositoryReplica> claimReplica(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            Instant leaseUntil,
            Instant now) {
        int updated = mapper.claimReplica(
                repositoryId.value(), generation, linuxServerId.value(), leaseToken, leaseUntil, now);
        if (updated != 1) {
            return Optional.empty();
        }
        return findReplicas(repositoryId).stream()
                .filter(replica -> replica.linuxServerId().equals(linuxServerId)
                        && replica.generation() == generation
                        && leaseToken.equals(replica.leaseToken()))
                .findFirst();
    }

    @Override
    public boolean renewLease(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            Instant leaseUntil,
            Instant now) {
        return mapper.renewLease(
                repositoryId.value(), generation, linuxServerId.value(), leaseToken, leaseUntil, now) == 1;
    }

    @Override
    public List<ReferenceRepositoryReplica> findClaimableReplicas(
            LinuxServerId linuxServerId,
            Instant now,
            int limit) {
        return mapper.findClaimableReplicas(linuxServerId.value(), now, Math.max(1, Math.min(1000, limit)))
                .stream().map(this::toReplica).toList();
    }

    @Override
    public boolean markReady(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            String branch,
            String commitHash,
            Instant syncedAt,
            Instant now) {
        return mapper.markReady(
                repositoryId.value(), generation, linuxServerId.value(), leaseToken,
                branch, commitHash, syncedAt, now) == 1;
    }

    @Override
    public boolean markRetry(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            int retryCount,
            Instant nextRetryAt,
            String lastError,
            Instant now) {
        return mapper.markRetry(
                repositoryId.value(), generation, linuxServerId.value(), leaseToken,
                retryCount, nextRetryAt, lastError, now) == 1;
    }

    @Override
    public boolean markBlocked(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            String lastError,
            Instant now) {
        return mapper.markBlocked(
                repositoryId.value(), generation, linuxServerId.value(), leaseToken, lastError, now) == 1;
    }

    @Override
    public boolean markVerificationResult(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            ReferenceRepositoryReplicaStatus status,
            String actualBranch,
            String actualCommitHash,
            Instant verifiedAt,
            String lastError,
            Instant now) {
        return mapper.markVerificationResult(
                repositoryId.value(), generation, linuxServerId.value(), leaseToken, status.name(),
                actualBranch, actualCommitHash, verifiedAt, lastError, now) == 1;
    }

    @Override
    public boolean updateOverallStatus(
            CodeRepositoryId repositoryId,
            long generation,
            ReferenceRepositoryStatus status,
            String lastError,
            Instant now) {
        return mapper.updateOverallStatus(repositoryId.value(), generation, status.name(), lastError, now) == 1;
    }

    private ReferenceRepositoryStateRow toRow(ReferenceRepositoryState state) {
        return new ReferenceRepositoryStateRow(
                state.repositoryId().value(),
                state.branch(),
                state.targetCommitHash(),
                state.generation(),
                state.status().name(),
                state.operationType().name(),
                state.credentialUserId() == null ? null : state.credentialUserId().value(),
                state.traceId(),
                state.lastError(),
                state.initializedAt(),
                state.createdAt(),
                state.updatedAt());
    }

    private ReferenceRepositoryState toState(ReferenceRepositoryStateRow row) {
        return new ReferenceRepositoryState(
                new CodeRepositoryId(row.repositoryId()),
                row.branch(),
                row.targetCommitHash(),
                row.generation(),
                ReferenceRepositoryStatus.valueOf(row.status()),
                ReferenceRepositoryOperationType.valueOf(row.operationType()),
                row.credentialUserId() == null ? null : new UserId(row.credentialUserId()),
                row.traceId(),
                row.lastError(),
                row.initializedAt(),
                row.createdAt(),
                row.updatedAt());
    }

    private ReferenceRepositoryReplica toReplica(ReferenceRepositoryReplicaRow row) {
        return new ReferenceRepositoryReplica(
                new CodeRepositoryId(row.repositoryId()),
                new LinuxServerId(row.linuxServerId()),
                row.generation(),
                ReferenceRepositoryReplicaStatus.valueOf(row.status()),
                row.branch(),
                row.currentCommitHash(),
                row.retryCount(),
                row.nextRetryAt(),
                row.leaseToken(),
                row.leaseUntil(),
                row.lastError(),
                row.syncedAt(),
                row.verifiedAt(),
                row.createdAt(),
                row.updatedAt());
    }
}
