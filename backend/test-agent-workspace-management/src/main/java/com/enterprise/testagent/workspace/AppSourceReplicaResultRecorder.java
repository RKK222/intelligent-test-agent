package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在副本 Git/文件系统工作结束后原子记录数据库结果。
 *
 * <p>第一台 READY 副本负责把 pending generation 提升为 active；其余服务器只补齐同一代副本。
 * 全部失败时只清 pending，旧 active 及其到期时间保持不变。</p>
 */
@Service
public class AppSourceReplicaResultRecorder {

    private final AppSourceRepository appSources;
    private final WorkspaceRepository workspaces;
    private final AppSourceReplicaProgressRecorder progress;

    @Autowired
    public AppSourceReplicaResultRecorder(
            AppSourceRepository appSources,
            WorkspaceRepository workspaces,
            AppSourceReplicaProgressRecorder progress) {
        this.appSources = Objects.requireNonNull(appSources, "appSources must not be null");
        this.workspaces = Objects.requireNonNull(workspaces, "workspaces must not be null");
        this.progress = Objects.requireNonNull(progress, "progress must not be null");
    }

    AppSourceReplicaResultRecorder(AppSourceRepository appSources, WorkspaceRepository workspaces) {
        this(appSources, workspaces, new AppSourceReplicaProgressRecorder(appSources));
    }

    /** 登记本服务器 READY 副本，并在需要时完成 generation 提升和旧工作区归档。 */
    @Transactional
    public void recordSuccess(
            AppSourceOperation operation,
            AppSourceReplica claimed,
            String leaseOwner,
            Workspace workspace,
            String indexSha256,
            Instant now) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(claimed, "claimed must not be null");
        Objects.requireNonNull(workspace, "workspace must not be null");
        AppSourceSnapshot finishingSnapshot = appSources
                .findSnapshot(claimed.repositoryId(), claimed.generation())
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码快照不存在"));
        if ((finishingSnapshot.status() != AppSourceSnapshotStatus.PENDING
                        && finishingSnapshot.status() != AppSourceSnapshotStatus.ACTIVE)
                || !finishingSnapshot.expiresAt().isAfter(now)) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码快照已到期或失效");
        }
        workspaces.save(workspace);
        AppSourceReplica ready = new AppSourceReplica(
                claimed.repositoryId(), claimed.generation(), claimed.linuxServerId(), workspace.workspaceId(),
                AppSourceReplicaStatus.READY, null, null, claimed.attemptCount(), null,
                null, null, claimed.createdAt(), now);
        requireLeaseUpdate(ready, leaseOwner, now);
        progress.completeAfterLeaseCas(operation, claimed, now);
        appSources.updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.PENDING, AppSourceOperationStatus.RUNNING, null);
        AppSourceRepositorySlot lockedSlot = requireLockedSlot(claimed.repositoryId());
        promoteIfPending(operation, claimed, indexSha256, now, lockedSlot);
        finishOperationIfTerminal(operation, now);
    }

    /** 登记安全化失败；最后一台失败时根据是否已有 READY 副本决定 PARTIAL_FAILED 或 FAILED。 */
    @Transactional
    public void recordFailure(
            AppSourceOperation operation,
            AppSourceReplica claimed,
            String leaseOwner,
            String safeErrorCode,
            String safeErrorMessage,
            Instant now) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(claimed, "claimed must not be null");
        AppSourceReplica failed = new AppSourceReplica(
                claimed.repositoryId(), claimed.generation(), claimed.linuxServerId(), null,
                AppSourceReplicaStatus.FAILED, null, null, claimed.attemptCount(), now,
                safeErrorCode, safeErrorMessage, claimed.createdAt(), now);
        requireLeaseUpdate(failed, leaseOwner, now);
        progress.failAfterLeaseCas(operation, claimed, now);
        appSources.updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.PENDING, AppSourceOperationStatus.RUNNING, null);

        AppSourceRepositorySlot lockedSlot = requireLockedSlot(claimed.repositoryId());
        List<AppSourceReplica> replicas = appSources.findReplicas(claimed.repositoryId(), claimed.generation());
        if (replicas.stream().anyMatch(this::notTerminal)) {
            return;
        }
        boolean anyReady = replicas.stream().anyMatch(replica -> replica.status() == AppSourceReplicaStatus.READY);
        if (anyReady) {
            appSources.updateOperationStatus(
                    operation.operationId(), AppSourceOperationStatus.RUNNING,
                    AppSourceOperationStatus.PARTIAL_FAILED, now);
            return;
        }
        failPendingGeneration(operation, now, lockedSlot);
    }

    /**
     * 收敛历史上已无可执行副本、但仍停留在非终态的操作。
     *
     * <p>恢复扫描只提供候选项；这里仍对 repository slot 加行锁并重新读取操作和副本，
     * 确保多 Java 实例重复扫描时只有持锁事务能够基于最新终态做聚合。</p>
     */
    @Transactional
    public void recoverTerminalOperation(String operationId, Instant now) {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        AppSourceOperation candidate = appSources.findOperation(operationId).orElse(null);
        if (candidate == null || candidate.status().terminal()) {
            return;
        }
        AppSourceRepositorySlot lockedSlot = requireLockedSlot(candidate.repositoryId());
        AppSourceOperation operation = appSources.findOperation(operationId).orElse(null);
        if (operation == null || operation.status().terminal()) {
            return;
        }
        List<AppSourceReplica> replicas = appSources.findReplicas(
                operation.repositoryId(), operation.targetGeneration());
        if (replicas.isEmpty() || replicas.stream().anyMatch(this::notTerminal)) {
            return;
        }
        if (operation.status() == AppSourceOperationStatus.PENDING
                && !appSources.updateOperationStatus(
                        operation.operationId(), AppSourceOperationStatus.PENDING,
                        AppSourceOperationStatus.RUNNING, null)) {
            return;
        }
        if (operation.status() != AppSourceOperationStatus.PENDING
                && operation.status() != AppSourceOperationStatus.RUNNING) {
            return;
        }
        for (AppSourceReplica replica : replicas) {
            if (replica.status() == AppSourceReplicaStatus.READY) {
                progress.completeAfterLeaseCas(operation, replica, now);
            } else {
                progress.failAfterLeaseCas(operation, replica, now);
            }
        }
        boolean anyReady = replicas.stream().anyMatch(replica -> replica.status() == AppSourceReplicaStatus.READY);
        if (anyReady) {
            boolean anyFailed = replicas.stream().anyMatch(replica -> replica.status() == AppSourceReplicaStatus.FAILED);
            appSources.updateOperationStatus(
                    operation.operationId(), AppSourceOperationStatus.RUNNING,
                    anyFailed ? AppSourceOperationStatus.PARTIAL_FAILED : AppSourceOperationStatus.SUCCEEDED,
                    now);
            return;
        }
        failPendingGeneration(operation, now, lockedSlot);
    }

    private void promoteIfPending(
            AppSourceOperation operation,
            AppSourceReplica claimed,
            String indexSha256,
            Instant now,
            AppSourceRepositorySlot slot) {
        if (Objects.equals(slot.activeGeneration(), claimed.generation())) {
            AppSourceSnapshot active = appSources.findSnapshot(claimed.repositoryId(), claimed.generation())
                    .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码快照不存在"));
            if (!Objects.equals(active.indexSha256(), indexSha256)) {
                throw new PlatformException(ErrorCode.CONFLICT, "应用源码索引摘要不一致");
            }
            return;
        }
        if (!Objects.equals(slot.pendingGeneration(), claimed.generation())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "应用源码副本 generation 已失效",
                    Map.of("generation", claimed.generation()));
        }
        Long previousGeneration = slot.activeGeneration();
        if (previousGeneration != null) {
            // PostgreSQL 以部分唯一索引保证每仓库至多一个 ACTIVE；同事务先失效旧代再激活新代。
            expirePreviousGeneration(claimed, previousGeneration, now);
        }
        boolean activated = appSources.updateSnapshotStatusAndIndex(
                claimed.repositoryId(), claimed.generation(), AppSourceSnapshotStatus.PENDING,
                AppSourceSnapshotStatus.ACTIVE, indexSha256, now);
        if (!activated) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码快照提升竞争失败");
        }
        AppSourceRepositorySlot promoted = new AppSourceRepositorySlot(
                slot.repositoryId(), claimed.generation(), null, slot.nextGeneration(),
                slot.latestOperationId(), slot.lockVersion() + 1L, slot.createdAt(), now);
        if (!appSources.updateSlotIfVersion(promoted, slot.lockVersion())) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码 generation 提升竞争失败");
        }
    }

    private void expirePreviousGeneration(
            AppSourceReplica claimed,
            long previousGeneration,
            Instant now) {
        AppSourceSnapshot previous = appSources.findSnapshot(claimed.repositoryId(), previousGeneration).orElse(null);
        if (previous != null && previous.status() == AppSourceSnapshotStatus.ACTIVE) {
            appSources.updateSnapshotStatusAndIndex(
                    claimed.repositoryId(), previousGeneration, AppSourceSnapshotStatus.ACTIVE,
                    AppSourceSnapshotStatus.EXPIRED, previous.indexSha256(), now);
        }
        appSources.makeCleanupDueNow(claimed.repositoryId(), previousGeneration, now);
        appSources.findReplica(claimed.repositoryId(), previousGeneration, claimed.linuxServerId())
                .map(AppSourceReplica::runtimeWorkspaceId)
                .flatMap(workspaces::findById)
                .ifPresent(workspace -> workspaces.save(new Workspace(
                        workspace.workspaceId(), workspace.name(), workspace.rootPath(), WorkspaceStatus.ARCHIVED,
                        workspace.createdAt(), now, workspace.linuxServerId(), workspace.traceId())));
    }

    private void failPendingGeneration(
            AppSourceOperation operation,
            Instant now,
            AppSourceRepositorySlot slot) {
        if (Objects.equals(slot.pendingGeneration(), operation.targetGeneration())) {
            appSources.updateSnapshotStatusAndIndex(
                    operation.repositoryId(), operation.targetGeneration(), AppSourceSnapshotStatus.PENDING,
                    AppSourceSnapshotStatus.FAILED, null, now);
            AppSourceRepositorySlot cleared = new AppSourceRepositorySlot(
                    slot.repositoryId(), slot.activeGeneration(), null, slot.nextGeneration(),
                    slot.latestOperationId(), slot.lockVersion() + 1L, slot.createdAt(), now);
            if (!appSources.updateSlotIfVersion(cleared, slot.lockVersion())) {
                throw new PlatformException(ErrorCode.CONFLICT, "应用源码失败状态收敛竞争失败");
            }
        }
        appSources.updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.FAILED, now);
    }

    private void finishOperationIfTerminal(AppSourceOperation operation, Instant now) {
        List<AppSourceReplica> replicas = appSources.findReplicas(
                operation.repositoryId(), operation.targetGeneration());
        if (replicas.isEmpty() || replicas.stream().anyMatch(this::notTerminal)) {
            return;
        }
        boolean anyFailed = replicas.stream().anyMatch(replica -> replica.status() == AppSourceReplicaStatus.FAILED);
        appSources.updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                anyFailed ? AppSourceOperationStatus.PARTIAL_FAILED : AppSourceOperationStatus.SUCCEEDED,
                now);
    }

    private boolean notTerminal(AppSourceReplica replica) {
        return replica.status() != AppSourceReplicaStatus.READY
                && replica.status() != AppSourceReplicaStatus.FAILED;
    }

    private void requireLeaseUpdate(AppSourceReplica replica, String leaseOwner, Instant now) {
        if (!appSources.updateReplicaIfLease(replica, leaseOwner, now)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT, "应用源码副本租约已失效",
                    Map.of("failure", "REPLICA_LEASE_LOST"));
        }
    }

    private AppSourceRepositorySlot requireLockedSlot(CodeRepositoryId repositoryId) {
        return appSources.findSlotForUpdate(repositoryId)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码 generation 槽位不存在"));
    }
}
