package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceReplica;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRunLease;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceState;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceGenerationStatus;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceOperationType;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 应用级自动化引用仓储的 MyBatis XML 实现。 */
@Repository
public class MyBatisApplicationAutomationReferenceRepository
        implements ApplicationAutomationReferenceRepository {

    private final ApplicationAutomationReferenceMapper mapper;

    public MyBatisApplicationAutomationReferenceRepository(ApplicationAutomationReferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ApplicationAutomationReferenceState ensureState(
            ApplicationId appId, CodeRepositoryId repositoryId, String traceId, Instant now) {
        mapper.insertStateIfAbsent(appId.value(), repositoryId.value(), traceId, now);
        return findState(appId, repositoryId)
                .orElseThrow(() -> new IllegalStateException("Automation reference state was not created"));
    }

    @Override
    public Optional<ApplicationAutomationReferenceState> findState(
            ApplicationId appId, CodeRepositoryId repositoryId) {
        return Optional.ofNullable(mapper.findState(appId.value(), repositoryId.value())).map(this::toState);
    }

    @Override
    public Optional<ApplicationAutomationReferenceGeneration> findGeneration(
            ApplicationId appId, CodeRepositoryId repositoryId, long generation) {
        return Optional.ofNullable(mapper.findGeneration(appId.value(), repositoryId.value(), generation))
                .map(this::toGeneration);
    }

    @Override
    public Optional<ApplicationAutomationReferenceGeneration> findByOperationId(
            ApplicationId appId, CodeRepositoryId repositoryId, String operationId) {
        return Optional.ofNullable(mapper.findByOperationId(appId.value(), repositoryId.value(), operationId))
                .map(this::toGeneration);
    }

    @Override
    public List<ApplicationAutomationReferenceGeneration> findRecoverableGenerations(int limit) {
        return mapper.findRecoverableGenerations(Math.max(1, Math.min(1000, limit))).stream()
                .map(this::toGeneration)
                .toList();
    }

    @Override
    public List<ApplicationAutomationReferenceReplica> findReplicas(
            ApplicationId appId, CodeRepositoryId repositoryId, long generation) {
        return mapper.findReplicas(appId.value(), repositoryId.value(), generation).stream()
                .map(this::toReplica)
                .toList();
    }

    @Override
    public List<LinuxServerId> findKnownServerIds(ApplicationId appId, CodeRepositoryId repositoryId) {
        return mapper.findKnownServerIds(appId.value(), repositoryId.value()).stream()
                .map(LinuxServerId::new)
                .toList();
    }

    @Override
    @Transactional
    public Optional<ApplicationAutomationReferenceGeneration> reserveGeneration(
            ApplicationAutomationReferenceGeneration generation,
            long expectedActiveGeneration,
            long expectedLockVersion,
            Instant now) {
        Optional<ApplicationAutomationReferenceGeneration> replay = findByOperationId(
                generation.appId(), generation.repositoryId(), generation.operationId());
        if (replay.isPresent()) {
            return replay;
        }
        ApplicationAutomationReferenceGenerationRow row = toRow(generation);
        if (mapper.reserveState(row, expectedActiveGeneration, expectedLockVersion, now) != 1) {
            return Optional.empty();
        }
        try {
            mapper.insertGeneration(row);
        } catch (DuplicateKeyException concurrentReplay) {
            throw concurrentReplay;
        }
        return findGeneration(generation.appId(), generation.repositoryId(), generation.generation());
    }

    @Override
    @Transactional
    public boolean beginVerification(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            long expectedLockVersion,
            String traceId,
            Collection<LinuxServerId> linuxServerIds,
            Instant now) {
        if (mapper.beginVerification(
                appId.value(), repositoryId.value(), generation, expectedLockVersion, traceId, now) != 1) {
            return false;
        }
        for (LinuxServerId serverId : linuxServerIds) {
            if (mapper.resetReplicaForVerification(
                    appId.value(), repositoryId.value(), generation, serverId.value(), now) == 0) {
                mapper.upsertTarget(appId.value(), repositoryId.value(), generation, serverId.value(), now);
            }
        }
        return true;
    }

    @Override
    @Transactional
    public void upsertTargets(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            Collection<LinuxServerId> linuxServerIds,
            Instant now) {
        for (LinuxServerId serverId : linuxServerIds) {
            mapper.upsertTarget(appId.value(), repositoryId.value(), generation, serverId.value(), now);
        }
    }

    @Override
    public int deferOfflineReplicas(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            Collection<LinuxServerId> liveServerIds,
            Instant now) {
        return mapper.deferOfflineReplicas(
                appId.value(), repositoryId.value(), generation,
                liveServerIds.stream().map(LinuxServerId::value).toList(), now);
    }

    @Override
    public List<ApplicationAutomationReferenceReplica> findClaimableReplicas(
            LinuxServerId linuxServerId, Instant now, int limit) {
        return mapper.findClaimableReplicas(linuxServerId.value(), now, Math.max(1, Math.min(1000, limit))).stream()
                .map(this::toReplica)
                .toList();
    }

    @Override
    @Transactional
    public Optional<ApplicationAutomationReferenceReplica> claimReplica(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            Instant leaseUntil,
            Instant now) {
        if (mapper.claimReplica(
                appId.value(), repositoryId.value(), generation, linuxServerId.value(),
                leaseToken, leaseUntil, now) != 1) {
            return Optional.empty();
        }
        return findReplicas(appId, repositoryId, generation).stream()
                .filter(replica -> replica.linuxServerId().equals(linuxServerId)
                        && leaseToken.equals(replica.leaseToken()))
                .findFirst();
    }

    @Override
    public boolean renewLease(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            Instant leaseUntil,
            Instant now) {
        return mapper.renewLease(
                appId.value(), repositoryId.value(), generation, linuxServerId.value(),
                leaseToken, leaseUntil, now) == 1;
    }

    @Override
    public boolean markReady(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            String branch,
            String commitHash,
            Instant syncedAt,
            Instant now) {
        return mapper.markReady(
                appId.value(), repositoryId.value(), generation, linuxServerId.value(), leaseToken,
                branch, commitHash, syncedAt, now) == 1;
    }

    @Override
    public boolean markRetry(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            int retryCount,
            Instant nextRetryAt,
            String lastError,
            Instant now) {
        return mapper.markRetry(
                appId.value(), repositoryId.value(), generation, linuxServerId.value(), leaseToken,
                retryCount, nextRetryAt, lastError, now) == 1;
    }

    @Override
    public boolean markBlocked(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            String lastError,
            Instant now) {
        return mapper.markBlocked(
                appId.value(), repositoryId.value(), generation, linuxServerId.value(),
                leaseToken, lastError, now) == 1;
    }

    @Override
    public boolean markVerificationResult(
            ApplicationId appId,
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
                appId.value(), repositoryId.value(), generation, linuxServerId.value(), leaseToken,
                status.name(), actualBranch, actualCommitHash, verifiedAt, lastError, now) == 1;
    }

    @Override
    @Transactional
    public boolean completeGeneration(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            ReferenceRepositoryStatus overallStatus,
            String lastError,
            Instant now) {
        ApplicationAutomationReferenceState state = findState(appId, repositoryId)
                .orElseThrow(() -> new IllegalStateException("Automation reference state was not found"));
        // Git 指针核验只更新运行状态和各服务器观测值，不能把仍在使用的不可变代次标成失败。
        // 否则一次核验异常会让当前只读引用立即失效，违背“核验不 fetch、不切换”的语义。
        if (state.operationType() != AutomationReferenceOperationType.VERIFY_POINTERS) {
            AutomationReferenceGenerationStatus generationStatus = overallStatus == ReferenceRepositoryStatus.READY
                    ? AutomationReferenceGenerationStatus.READY
                    : AutomationReferenceGenerationStatus.FAILED;
            mapper.updateGenerationResult(
                    appId.value(), repositoryId.value(), generation, generationStatus.name(),
                    lastError, overallStatus == ReferenceRepositoryStatus.READY ? now : null, now);
        }
        return mapper.completeState(
                appId.value(), repositoryId.value(), generation, overallStatus.name(), lastError, now) == 1;
    }

    @Override
    @Transactional
    public boolean terminateOperation(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            long expectedLockVersion,
            String lastError,
            Instant now) {
        if (mapper.terminateState(
                appId.value(), repositoryId.value(), generation, expectedLockVersion, lastError, now) != 1) {
            return false;
        }
        mapper.terminateGeneration(appId.value(), repositoryId.value(), generation, lastError, now);
        mapper.terminateReplicas(appId.value(), repositoryId.value(), generation, lastError, now);
        return true;
    }

    @Override
    @Transactional
    public void replaceRunLeases(
            RunId runId,
            Collection<ApplicationAutomationReferenceRunLease> leases,
            Instant now) {
        mapper.deleteRunLeases(runId.value());
        for (ApplicationAutomationReferenceRunLease lease : leases) {
            String lockedStatus = mapper.lockReadyGeneration(
                    lease.appId().value(), lease.repositoryId().value(), lease.generation());
            if (lockedStatus == null) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "自动化引用配置已切换，请重试本次运行");
            }
            mapper.insertRunLease(
                    runId.value(),
                    lease.appId().value(),
                    lease.repositoryId().value(),
                    lease.generation(),
                    lease.linuxServerId().value(),
                    now);
        }
    }

    @Override
    public void deleteRunLeases(RunId runId) {
        mapper.deleteRunLeases(runId.value());
    }

    @Override
    public boolean saveReadLease(
            String tokenHash,
            UserId userId,
            WorkspaceId workspaceId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            Instant expiresAt,
            Instant now) {
        return mapper.insertReadLeaseForActiveGeneration(
                tokenHash, userId.value(), workspaceId.value(), appId.value(), repositoryId.value(),
                generation, expiresAt, now) == 1;
    }

    @Override
    public boolean renewReadLease(
            String tokenHash,
            UserId userId,
            WorkspaceId workspaceId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            Instant nextExpiresAt,
            Instant now) {
        return mapper.renewReadLease(
                tokenHash, userId.value(), workspaceId.value(), appId.value(), repositoryId.value(),
                generation, nextExpiresAt, now) == 1;
    }

    @Override
    public int deleteExpiredReadLeases(Instant now) {
        return mapper.deleteExpiredReadLeases(now);
    }

    @Override
    public List<ApplicationAutomationReferenceGeneration> findRetirableGenerations(Instant now, int limit) {
        return mapper.findRetirableGenerations(now, Math.max(1, Math.min(1000, limit))).stream()
                .map(this::toGeneration)
                .toList();
    }

    @Override
    public boolean retireGeneration(
            ApplicationId appId, CodeRepositoryId repositoryId, long generation, Instant now) {
        return mapper.retireGeneration(appId.value(), repositoryId.value(), generation, now) == 1;
    }

    @Override
    public List<ApplicationAutomationReferenceReplica> findRetiredReplicas(
            LinuxServerId linuxServerId, int limit) {
        return mapper.findRetiredReplicas(linuxServerId.value(), Math.max(1, Math.min(1000, limit))).stream()
                .map(this::toReplica)
                .toList();
    }

    @Override
    public boolean deleteRetiredReplica(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId) {
        return mapper.deleteRetiredReplica(
                appId.value(), repositoryId.value(), generation, linuxServerId.value()) == 1;
    }

    private ApplicationAutomationReferenceState toState(ApplicationAutomationReferenceStateRow row) {
        return new ApplicationAutomationReferenceState(
                new ApplicationId(row.appId()),
                new CodeRepositoryId(row.repositoryId()),
                row.activeGeneration(),
                row.pendingGeneration(),
                row.nextGeneration(),
                row.lockVersion(),
                ReferenceRepositoryStatus.valueOf(row.status()),
                AutomationReferenceOperationType.valueOf(row.operationType()),
                row.traceId(),
                row.lastError(),
                row.createdAt(),
                row.updatedAt());
    }

    private ApplicationAutomationReferenceGeneration toGeneration(
            ApplicationAutomationReferenceGenerationRow row) {
        return new ApplicationAutomationReferenceGeneration(
                new ApplicationId(row.appId()),
                new CodeRepositoryId(row.repositoryId()),
                row.generation(),
                row.branch(),
                row.directoryPath(),
                row.description(),
                row.referenceAlias(),
                row.mergeEnabled(),
                row.targetCommitHash(),
                AutomationReferenceGenerationStatus.valueOf(row.status()),
                AutomationReferenceOperationType.valueOf(row.operationType()),
                new UserId(row.operatedByUserId()),
                row.operationId(),
                row.traceId(),
                row.lastError(),
                row.activatedAt(),
                row.createdAt(),
                row.updatedAt());
    }

    private ApplicationAutomationReferenceGenerationRow toRow(
            ApplicationAutomationReferenceGeneration generation) {
        return new ApplicationAutomationReferenceGenerationRow(
                generation.appId().value(),
                generation.repositoryId().value(),
                generation.generation(),
                generation.branch(),
                generation.directoryPath(),
                generation.description(),
                generation.referenceAlias(),
                generation.merge(),
                generation.targetCommitHash(),
                generation.status().name(),
                generation.operationType().name(),
                generation.operatedByUserId().value(),
                generation.operationId(),
                generation.traceId(),
                generation.lastError(),
                generation.activatedAt(),
                generation.createdAt(),
                generation.updatedAt());
    }

    private ApplicationAutomationReferenceReplica toReplica(ApplicationAutomationReferenceReplicaRow row) {
        return new ApplicationAutomationReferenceReplica(
                new ApplicationId(row.appId()),
                new CodeRepositoryId(row.repositoryId()),
                row.generation(),
                new LinuxServerId(row.linuxServerId()),
                ReferenceRepositoryReplicaStatus.valueOf(row.status()),
                row.currentBranch(),
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
