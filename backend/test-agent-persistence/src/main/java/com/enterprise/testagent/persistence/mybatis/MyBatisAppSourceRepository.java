package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.appsource.AppSourceCleanupStatus;
import com.enterprise.testagent.domain.appsource.AppSourceCleanupTask;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourceRecentSelection;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.appsource.AppSourceStepScope;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.CleanupRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.OperationRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.RecentRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.ReplicaRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.SlotRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.SnapshotRow;
import com.enterprise.testagent.persistence.mybatis.AppSourceRows.StepRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 应用源码领域端口的 MyBatis XML 实现。 */
@Repository
public class MyBatisAppSourceRepository implements AppSourceRepository {

    private static final TypeReference<List<AppSourceSelectedPath>> SELECTED_PATHS_TYPE = new TypeReference<>() {
    };

    private final AppSourceMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisAppSourceRepository(AppSourceMapper mapper) {
        this(mapper, new ObjectMapper());
    }

    MyBatisAppSourceRepository(AppSourceMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean hasRepositoryHistory(CodeRepositoryId repositoryId) {
        return mapper.hasRepositoryHistory(repositoryId.value());
    }

    @Override
    public Optional<AppSourceRepositorySlot> findSlot(CodeRepositoryId repositoryId) {
        return Optional.ofNullable(mapper.findSlot(repositoryId.value())).map(this::toSlot);
    }

    @Override
    public Optional<AppSourceRepositorySlot> findSlotForUpdate(CodeRepositoryId repositoryId) {
        return Optional.ofNullable(mapper.findSlotForUpdate(repositoryId.value())).map(this::toSlot);
    }

    @Override
    public boolean insertSlotIfAbsent(AppSourceRepositorySlot slot) {
        try {
            return mapper.insertSlotIfAbsent(toRow(slot)) == 1;
        } catch (DuplicateKeyException concurrentWinner) {
            // H2 MERGE 并发进入 NOT MATCHED 时唯一键冲突等价于 insert-if-absent 竞争失败。
            return false;
        }
    }

    @Override
    public boolean updateSlotIfVersion(AppSourceRepositorySlot slot, long expectedLockVersion) {
        return mapper.updateSlotIfVersion(toRow(slot), expectedLockVersion) == 1;
    }

    @Override
    public Optional<AppSourceSnapshot> findSnapshot(CodeRepositoryId repositoryId, long generation) {
        return Optional.ofNullable(mapper.findSnapshot(repositoryId.value(), generation)).map(this::toSnapshot);
    }

    @Override
    public Optional<AppSourceSnapshot> findActiveSnapshot(CodeRepositoryId repositoryId) {
        return Optional.ofNullable(mapper.findActiveSnapshot(repositoryId.value())).map(this::toSnapshot);
    }

    @Override
    public void saveSnapshot(AppSourceSnapshot snapshot) {
        mapper.insertSnapshot(toRow(snapshot));
    }

    @Override
    public boolean updateSnapshotStatusAndIndex(
            CodeRepositoryId repositoryId,
            long generation,
            AppSourceSnapshotStatus expectedStatus,
            AppSourceSnapshotStatus nextStatus,
            String indexSha256,
            Instant updatedAt) {
        if (!expectedStatus.canTransitionTo(nextStatus)) {
            throw new IllegalArgumentException("invalid app-source snapshot status transition");
        }
        return mapper.updateSnapshotStatusAndIndex(
                repositoryId.value(), generation, expectedStatus.name(), nextStatus.name(), indexSha256, updatedAt) == 1;
    }

    @Override
    public Optional<AppSourceReplica> findReplica(
            CodeRepositoryId repositoryId, long generation, LinuxServerId linuxServerId) {
        return Optional.ofNullable(mapper.findReplica(repositoryId.value(), generation, linuxServerId.value()))
                .map(this::toReplica);
    }

    @Override
    public List<AppSourceReplica> findReplicas(CodeRepositoryId repositoryId, long generation) {
        return mapper.findReplicas(repositoryId.value(), generation).stream().map(this::toReplica).toList();
    }

    @Override
    public void saveReplica(AppSourceReplica replica) {
        mapper.upsertReplica(toRow(replica));
    }

    @Override
    public Optional<AppSourceReplica> claimReplica(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseOwner,
            Instant leaseUntil,
            Instant now) {
        if (mapper.claimReplica(
                repositoryId.value(), generation, linuxServerId.value(), leaseOwner, leaseUntil, now) != 1) {
            return Optional.empty();
        }
        return findReplica(repositoryId, generation, linuxServerId);
    }

    @Override
    public boolean updateReplicaIfLease(AppSourceReplica replica, String expectedLeaseOwner, Instant now) {
        return mapper.updateReplicaIfLease(toRow(replica), expectedLeaseOwner, now) == 1;
    }

    @Override
    public Optional<AppSourceOperation> findOperation(String operationId) {
        return Optional.ofNullable(mapper.findOperation(operationId)).map(this::toOperation);
    }

    @Override
    public Optional<AppSourceOperation> findLatestOperation(CodeRepositoryId repositoryId) {
        return Optional.ofNullable(mapper.findLatestOperation(repositoryId.value())).map(this::toOperation);
    }

    @Override
    public void saveOperation(AppSourceOperation operation) {
        mapper.insertOperation(toRow(operation));
    }

    @Override
    public boolean updateOperationStatus(
            String operationId,
            AppSourceOperationStatus expectedStatus,
            AppSourceOperationStatus nextStatus,
            Instant completedAt) {
        if (!expectedStatus.canTransitionTo(nextStatus)
                || nextStatus.terminal() != (completedAt != null)) {
            throw new IllegalArgumentException("invalid app-source operation status transition");
        }
        return mapper.updateOperationStatus(operationId, expectedStatus.name(), nextStatus.name(), completedAt) == 1;
    }

    @Override
    public void upsertStep(AppSourceOperationStep step) {
        mapper.upsertStep(toRow(step));
    }

    @Override
    public List<AppSourceOperationStep> findSteps(String operationId) {
        return mapper.findSteps(operationId).stream().map(this::toStep).toList();
    }

    @Override
    @Transactional
    public void insertCleanupTasks(List<AppSourceCleanupTask> tasks) {
        for (AppSourceCleanupTask task : List.copyOf(tasks)) {
            mapper.insertCleanupIfAbsent(toRow(task));
        }
    }

    @Override
    public List<AppSourceCleanupTask> findDueCleanupTasks(
            LinuxServerId linuxServerId, Instant now, int limit) {
        return mapper.findDueCleanupTasks(linuxServerId.value(), now, boundedLimit(limit)).stream()
                .map(this::toCleanup)
                .toList();
    }

    @Override
    public Optional<AppSourceCleanupTask> claimCleanupTask(
            String cleanupTaskId, String leaseOwner, Instant leaseUntil, Instant now) {
        if (mapper.claimCleanupTask(cleanupTaskId, leaseOwner, leaseUntil, now) != 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findCleanupTask(cleanupTaskId)).map(this::toCleanup);
    }

    @Override
    public boolean rescheduleCleanupTask(
            String cleanupTaskId,
            String leaseOwner,
            int attemptCount,
            Instant nextRetryAt,
            String safeErrorCode,
            String safeErrorMessage,
            Instant now) {
        return mapper.rescheduleCleanupTask(
                cleanupTaskId, leaseOwner, attemptCount, nextRetryAt,
                safeErrorCode, safeErrorMessage, now) == 1;
    }

    @Override
    public boolean completeCleanupTask(String cleanupTaskId, String leaseOwner, Instant now) {
        return mapper.completeCleanupTask(cleanupTaskId, leaseOwner, now) == 1;
    }

    @Override
    public boolean supersedeCleanupTask(String cleanupTaskId, Instant now) {
        return mapper.supersedeCleanupTask(cleanupTaskId, now) == 1;
    }

    @Override
    public List<AppSourceCleanupTask> findCleanupTasks(
            CodeRepositoryId repositoryId, long generation, LinuxServerId linuxServerId) {
        return mapper.findCleanupTasks(repositoryId.value(), generation, linuxServerId.value()).stream()
                .map(this::toCleanup)
                .toList();
    }

    @Override
    public Optional<AppSourceRecentSelection> findRecentSelection(UserId userId) {
        return Optional.ofNullable(mapper.findRecentSelection(userId.value())).map(this::toRecent);
    }

    @Override
    public void upsertRecentSelection(AppSourceRecentSelection selection) {
        mapper.upsertRecentSelection(toRow(selection));
    }

    @Override
    public void deleteRecentSelection(UserId userId) {
        mapper.deleteRecentSelection(userId.value());
    }

    private SlotRow toRow(AppSourceRepositorySlot slot) {
        return new SlotRow(slot.repositoryId().value(), slot.activeGeneration(), slot.pendingGeneration(),
                slot.nextGeneration(), slot.latestOperationId(), slot.lockVersion(), slot.createdAt(), slot.updatedAt());
    }

    private AppSourceRepositorySlot toSlot(SlotRow row) {
        return new AppSourceRepositorySlot(new CodeRepositoryId(row.repositoryId()), row.activeGeneration(),
                row.pendingGeneration(), row.nextGeneration(), row.latestOperationId(), row.lockVersion(),
                row.createdAt(), row.updatedAt());
    }

    private SnapshotRow toRow(AppSourceSnapshot snapshot) {
        return new SnapshotRow(snapshot.repositoryId().value(), snapshot.generation(), snapshot.repositoryEnglishName(),
                snapshot.purpose().name(), snapshot.ownerUserId() == null ? null : snapshot.ownerUserId().value(),
                snapshot.branch(), snapshot.targetCommit(), writeSelectedPaths(snapshot.selectedPaths()),
                snapshot.indexSha256(), snapshot.acceptedAt(), snapshot.expiresAt(), snapshot.status().name(),
                snapshot.createdAt(), snapshot.updatedAt());
    }

    private AppSourceSnapshot toSnapshot(SnapshotRow row) {
        return new AppSourceSnapshot(new CodeRepositoryId(row.repositoryId()), row.generation(),
                row.repositoryEnglishName(), com.enterprise.testagent.domain.appsource.AppSourcePurpose.valueOf(row.purpose()),
                row.ownerUserId() == null ? null : new UserId(row.ownerUserId()), row.branch(), row.targetCommit(),
                readSelectedPaths(row.selectedPathsJson()), row.indexSha256(), row.acceptedAt(), row.expiresAt(),
                AppSourceSnapshotStatus.valueOf(row.status()), row.createdAt(), row.updatedAt());
    }

    private ReplicaRow toRow(AppSourceReplica replica) {
        return new ReplicaRow(replica.repositoryId().value(), replica.generation(), replica.linuxServerId().value(),
                replica.runtimeWorkspaceId() == null ? null : replica.runtimeWorkspaceId().value(), replica.status().name(),
                replica.leaseOwner(), replica.leaseUntil(), replica.attemptCount(), replica.nextRetryAt(),
                replica.safeErrorCode(), replica.safeErrorMessage(), replica.createdAt(), replica.updatedAt());
    }

    private AppSourceReplica toReplica(ReplicaRow row) {
        return new AppSourceReplica(new CodeRepositoryId(row.repositoryId()), row.generation(),
                new LinuxServerId(row.linuxServerId()),
                row.runtimeWorkspaceId() == null ? null : new WorkspaceId(row.runtimeWorkspaceId()),
                AppSourceReplicaStatus.valueOf(row.status()), row.leaseOwner(), row.leaseUntil(), row.attemptCount(),
                row.nextRetryAt(), row.safeErrorCode(), row.safeErrorMessage(), row.createdAt(), row.updatedAt());
    }

    private OperationRow toRow(AppSourceOperation operation) {
        return new OperationRow(operation.operationId(), operation.appId().value(), operation.repositoryId().value(),
                operation.sourceGeneration(), operation.targetGeneration(), operation.actorUserId().value(),
                operation.operationType().name(), operation.requestHash(), operation.status().name(), operation.traceId(),
                operation.acceptedAt(), operation.completedAt());
    }

    private AppSourceOperation toOperation(OperationRow row) {
        return new AppSourceOperation(row.operationId(), new ApplicationId(row.appId()),
                new CodeRepositoryId(row.repositoryId()), row.sourceGeneration(), row.targetGeneration(),
                new UserId(row.actorUserId()), AppSourceOperationType.valueOf(row.operationType()), row.requestHash(),
                AppSourceOperationStatus.valueOf(row.status()), row.traceId(), row.acceptedAt(), row.completedAt());
    }

    private StepRow toRow(AppSourceOperationStep step) {
        return new StepRow(step.stepId(), step.operationId(), step.scope().name(),
                step.linuxServerId() == null ? null : step.linuxServerId().value(), step.stepCode(), step.sequence(),
                step.status().name(), step.safeSummary(), step.startedAt(), step.completedAt(), step.updatedAt());
    }

    private AppSourceOperationStep toStep(StepRow row) {
        return new AppSourceOperationStep(row.stepId(), row.operationId(), AppSourceStepScope.valueOf(row.scope()),
                row.linuxServerId() == null ? null : new LinuxServerId(row.linuxServerId()), row.stepCode(),
                row.sequence(), AppSourceStepStatus.valueOf(row.status()), row.safeSummary(), row.startedAt(),
                row.completedAt(), row.updatedAt());
    }

    private CleanupRow toRow(AppSourceCleanupTask task) {
        return new CleanupRow(task.cleanupTaskId(), task.operationId(), task.repositoryId().value(), task.generation(),
                task.linuxServerId().value(), task.deleteAt(), task.status().name(), task.leaseOwner(), task.leaseUntil(),
                task.attemptCount(), task.nextRetryAt(), task.safeErrorCode(), task.safeErrorMessage(), task.traceId(),
                task.createdAt(), task.updatedAt());
    }

    private AppSourceCleanupTask toCleanup(CleanupRow row) {
        return new AppSourceCleanupTask(row.cleanupTaskId(), row.operationId(),
                new CodeRepositoryId(row.repositoryId()), row.generation(), new LinuxServerId(row.linuxServerId()),
                row.deleteAt(), AppSourceCleanupStatus.valueOf(row.status()), row.leaseOwner(), row.leaseUntil(),
                row.attemptCount(), row.nextRetryAt(), row.safeErrorCode(), row.safeErrorMessage(), row.traceId(),
                row.createdAt(), row.updatedAt());
    }

    private RecentRow toRow(AppSourceRecentSelection selection) {
        return new RecentRow(selection.userId().value(), selection.appId().value(), selection.repositoryId().value(),
                selection.generation(), selection.updatedAt());
    }

    private AppSourceRecentSelection toRecent(RecentRow row) {
        return new AppSourceRecentSelection(new UserId(row.userId()), new ApplicationId(row.appId()),
                new CodeRepositoryId(row.repositoryId()), row.generation(), row.updatedAt());
    }

    private String writeSelectedPaths(List<AppSourceSelectedPath> selectedPaths) {
        try {
            return objectMapper.writeValueAsString(selectedPaths);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("cannot serialize app-source selected paths", exception);
        }
    }

    private List<AppSourceSelectedPath> readSelectedPaths(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            // H2 JSON JDBC 会把参数化 JSON 再包一层字符串；PostgreSQL JSONB 直接返回数组。
            String normalized = root.isTextual() ? root.textValue() : root.toString();
            return objectMapper.readValue(normalized, SELECTED_PATHS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot deserialize app-source selected paths", exception);
        }
    }

    private int boundedLimit(int limit) {
        return Math.max(1, Math.min(200, limit));
    }
}
