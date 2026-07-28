package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.persistence.mybatis.AppSourceRows.CleanupRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.OperationRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.RecentRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.ReplicaRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.SlotRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.SnapshotRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.StepRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 应用源码 MyBatis mapper；所有关系型 SQL 只维护在 XML。 */
@Mapper
public interface AppSourceMapper {

    String lockRepositoryForAppSource(@Param("repositoryId") String repositoryId);

    boolean hasRepositoryHistory(@Param("repositoryId") String repositoryId);

    SlotRow findSlot(@Param("repositoryId") String repositoryId);

    SlotRow findSlotForUpdate(@Param("repositoryId") String repositoryId);

    int insertSlotIfAbsent(@Param("row") SlotRow row);

    int updateSlotIfVersion(@Param("row") SlotRow row, @Param("expectedLockVersion") long expectedLockVersion);

    SnapshotRow findSnapshot(@Param("repositoryId") String repositoryId, @Param("generation") long generation);

    SnapshotRow findActiveSnapshot(@Param("repositoryId") String repositoryId);

    int insertSnapshot(@Param("row") SnapshotRow row);

    int updateSnapshotStatusAndIndex(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus,
            @Param("indexSha256") String indexSha256,
            @Param("updatedAt") Instant updatedAt);

    ReplicaRow findReplica(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId);

    List<ReplicaRow> findReplicas(
            @Param("repositoryId") String repositoryId, @Param("generation") long generation);

    List<ReplicaRow> findClaimableReplicas(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now,
            @Param("limit") int limit);

    ReplicaRow findReplicaByRuntimeWorkspaceId(@Param("runtimeWorkspaceId") String runtimeWorkspaceId);

    int insertReplicaIfAbsent(@Param("row") ReplicaRow row);

    int claimReplica(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int updateReplicaIfLease(
            @Param("row") ReplicaRow row,
            @Param("expectedLeaseOwner") String expectedLeaseOwner,
            @Param("now") Instant now);

    String lockReplicaLeaseForUpdate(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("expectedLeaseOwner") String expectedLeaseOwner,
            @Param("now") Instant now);

    int markReplicaCleaned(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    OperationRow findOperation(@Param("operationId") String operationId);

    OperationRow findLatestOperation(@Param("repositoryId") String repositoryId);

    OperationRow findInFlightOperationForReplica(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId);

    List<OperationRow> findStrandedOperations(@Param("limit") int limit);

    int insertOperation(@Param("row") OperationRow row);

    int updateOperationStatus(
            @Param("operationId") String operationId,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus,
            @Param("completedAt") Instant completedAt);

    int upsertStep(@Param("row") StepRow row);

    int updateStepIfReplicaLease(
            @Param("row") StepRow row,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("expectedLeaseOwner") String expectedLeaseOwner,
            @Param("now") Instant now);

    int resetStepIfReplicaLease(
            @Param("row") StepRow row,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("expectedLeaseOwner") String expectedLeaseOwner,
            @Param("now") Instant now);

    List<StepRow> findSteps(@Param("operationId") String operationId);

    int insertCleanupIfAbsent(@Param("row") CleanupRow row);

    List<CleanupRow> findDueCleanupTasks(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now,
            @Param("limit") int limit);

    CleanupRow findCleanupTask(@Param("cleanupTaskId") String cleanupTaskId);

    int claimCleanupTask(
            @Param("cleanupTaskId") String cleanupTaskId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int rescheduleCleanupTask(
            @Param("cleanupTaskId") String cleanupTaskId,
            @Param("leaseOwner") String leaseOwner,
            @Param("attemptCount") int attemptCount,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("safeErrorCode") String safeErrorCode,
            @Param("safeErrorMessage") String safeErrorMessage,
            @Param("now") Instant now);

    int completeCleanupTask(
            @Param("cleanupTaskId") String cleanupTaskId,
            @Param("leaseOwner") String leaseOwner,
            @Param("now") Instant now);

    int supersedeCleanupTask(@Param("cleanupTaskId") String cleanupTaskId, @Param("now") Instant now);

    List<CleanupRow> findCleanupTasks(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId);

    int makeCleanupDueNow(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("now") Instant now);

    RecentRow findRecentSelection(@Param("userId") String userId);

    int upsertRecentSelection(@Param("row") RecentRow row);

    int deleteRecentSelection(@Param("userId") String userId);
}
