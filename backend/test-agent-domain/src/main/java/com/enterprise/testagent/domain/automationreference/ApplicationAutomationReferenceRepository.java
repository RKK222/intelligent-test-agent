package com.enterprise.testagent.domain.automationreference;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.run.RunId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 应用级自动化引用状态、不可变代次与共享副本的持久化端口。 */
public interface ApplicationAutomationReferenceRepository {

    ApplicationAutomationReferenceState ensureState(
            ApplicationId appId, CodeRepositoryId repositoryId, String traceId, Instant now);

    Optional<ApplicationAutomationReferenceState> findState(ApplicationId appId, CodeRepositoryId repositoryId);

    Optional<ApplicationAutomationReferenceGeneration> findGeneration(
            ApplicationId appId, CodeRepositoryId repositoryId, long generation);

    Optional<ApplicationAutomationReferenceGeneration> findByOperationId(
            ApplicationId appId, CodeRepositoryId repositoryId, String operationId);

    List<ApplicationAutomationReferenceGeneration> findRecoverableGenerations(int limit);

    List<ApplicationAutomationReferenceReplica> findReplicas(
            ApplicationId appId, CodeRepositoryId repositoryId, long generation);

    List<LinuxServerId> findKnownServerIds(ApplicationId appId, CodeRepositoryId repositoryId);

    Optional<ApplicationAutomationReferenceGeneration> reserveGeneration(
            ApplicationAutomationReferenceGeneration generation,
            long expectedActiveGeneration,
            long expectedLockVersion,
            Instant now);

    boolean beginVerification(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            long expectedLockVersion,
            String traceId,
            Collection<LinuxServerId> linuxServerIds,
            Instant now);

    void upsertTargets(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            Collection<LinuxServerId> linuxServerIds,
            Instant now);

    int deferOfflineReplicas(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            Collection<LinuxServerId> liveServerIds,
            Instant now);

    List<ApplicationAutomationReferenceReplica> findClaimableReplicas(
            LinuxServerId linuxServerId, Instant now, int limit);

    Optional<ApplicationAutomationReferenceReplica> claimReplica(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            Instant leaseUntil,
            Instant now);

    boolean renewLease(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            Instant leaseUntil,
            Instant now);

    boolean markReady(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            String branch,
            String commitHash,
            Instant syncedAt,
            Instant now);

    boolean markRetry(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            int retryCount,
            Instant nextRetryAt,
            String lastError,
            Instant now);

    boolean markBlocked(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            String lastError,
            Instant now);

    boolean markVerificationResult(
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
            Instant now);

    boolean completeGeneration(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            ReferenceRepositoryStatus overallStatus,
            String lastError,
            Instant now);

    boolean terminateOperation(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            long expectedLockVersion,
            String lastError,
            Instant now);

    void replaceRunLeases(
            RunId runId,
            Collection<ApplicationAutomationReferenceRunLease> leases,
            Instant now);

    void deleteRunLeases(RunId runId);
}
