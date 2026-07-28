package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 应用源码快照持久化端口；关系型实现必须通过 MyBatis XML 并保留 CAS/fencing 条件。 */
public interface AppSourceRepository extends AppSourceRepositoryHistory {

    Optional<AppSourceRepositorySlot> findSlot(CodeRepositoryId repositoryId);

    Optional<AppSourceRepositorySlot> findSlotForUpdate(CodeRepositoryId repositoryId);

    boolean insertSlotIfAbsent(AppSourceRepositorySlot slot);

    boolean updateSlotIfVersion(AppSourceRepositorySlot slot, long expectedLockVersion);

    Optional<AppSourceSnapshot> findSnapshot(CodeRepositoryId repositoryId, long generation);

    Optional<AppSourceSnapshot> findActiveSnapshot(CodeRepositoryId repositoryId);

    void saveSnapshot(AppSourceSnapshot snapshot);

    boolean updateSnapshotStatusAndIndex(
            CodeRepositoryId repositoryId,
            long generation,
            AppSourceSnapshotStatus expectedStatus,
            AppSourceSnapshotStatus nextStatus,
            String indexSha256,
            Instant updatedAt);

    Optional<AppSourceReplica> findReplica(
            CodeRepositoryId repositoryId, long generation, LinuxServerId linuxServerId);

    List<AppSourceReplica> findReplicas(CodeRepositoryId repositoryId, long generation);

    void saveReplica(AppSourceReplica replica);

    Optional<AppSourceReplica> claimReplica(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseOwner,
            Instant leaseUntil,
            Instant now);

    boolean updateReplicaIfLease(AppSourceReplica replica, String expectedLeaseOwner, Instant now);

    Optional<AppSourceOperation> findOperation(String operationId);

    Optional<AppSourceOperation> findLatestOperation(CodeRepositoryId repositoryId);

    void saveOperation(AppSourceOperation operation);

    boolean updateOperationStatus(
            String operationId,
            AppSourceOperationStatus expectedStatus,
            AppSourceOperationStatus nextStatus,
            Instant completedAt);

    void upsertStep(AppSourceOperationStep step);

    List<AppSourceOperationStep> findSteps(String operationId);

    void insertCleanupTasks(List<AppSourceCleanupTask> tasks);

    List<AppSourceCleanupTask> findDueCleanupTasks(LinuxServerId linuxServerId, Instant now, int limit);

    Optional<AppSourceCleanupTask> claimCleanupTask(
            String cleanupTaskId, String leaseOwner, Instant leaseUntil, Instant now);

    boolean rescheduleCleanupTask(
            String cleanupTaskId,
            String leaseOwner,
            int attemptCount,
            Instant nextRetryAt,
            String safeErrorCode,
            String safeErrorMessage,
            Instant now);

    boolean completeCleanupTask(String cleanupTaskId, String leaseOwner, Instant now);

    boolean supersedeCleanupTask(String cleanupTaskId, Instant now);

    List<AppSourceCleanupTask> findCleanupTasks(
            CodeRepositoryId repositoryId, long generation, LinuxServerId linuxServerId);

    Optional<AppSourceRecentSelection> findRecentSelection(UserId userId);

    void upsertRecentSelection(AppSourceRecentSelection selection);

    void deleteRecentSelection(UserId userId);
}
