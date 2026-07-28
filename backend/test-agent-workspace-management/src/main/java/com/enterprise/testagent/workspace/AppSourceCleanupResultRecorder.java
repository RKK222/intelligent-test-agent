package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceCleanupTask;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 清理磁盘成功后原子归档 Workspace、副本、快照和 cleanup task。 */
@Service
public class AppSourceCleanupResultRecorder {

    private final AppSourceRepository appSources;
    private final WorkspaceRepository workspaces;

    public AppSourceCleanupResultRecorder(AppSourceRepository appSources, WorkspaceRepository workspaces) {
        this.appSources = Objects.requireNonNull(appSources);
        this.workspaces = Objects.requireNonNull(workspaces);
    }

    @Transactional
    public void complete(AppSourceCleanupTask task, String leaseOwner, Instant now) {
        AppSourceRepositorySlot slot = appSources.findSlotForUpdate(task.repositoryId()).orElse(null);
        AppSourceSnapshot snapshot = appSources.findSnapshot(task.repositoryId(), task.generation()).orElse(null);
        if (snapshot != null) {
            AppSourceSnapshotStatus status = snapshot.status();
            if (status == AppSourceSnapshotStatus.ACTIVE) {
                appSources.updateSnapshotStatusAndIndex(
                        task.repositoryId(), task.generation(), status,
                        AppSourceSnapshotStatus.EXPIRED, snapshot.indexSha256(), now);
                status = AppSourceSnapshotStatus.EXPIRED;
            }
            if (status == AppSourceSnapshotStatus.EXPIRED || status == AppSourceSnapshotStatus.FAILED) {
                appSources.updateSnapshotStatusAndIndex(
                        task.repositoryId(), task.generation(), status,
                        AppSourceSnapshotStatus.CLEANED, snapshot.indexSha256(), now);
            }
        }
        if (slot != null && Objects.equals(slot.activeGeneration(), task.generation())) {
            AppSourceRepositorySlot cleared = new AppSourceRepositorySlot(
                    slot.repositoryId(), null, slot.pendingGeneration(), slot.nextGeneration(),
                    slot.latestOperationId(), slot.lockVersion() + 1L, slot.createdAt(), now);
            if (!appSources.updateSlotIfVersion(cleared, slot.lockVersion())) {
                throw new PlatformException(ErrorCode.CONFLICT, "应用源码清理 generation 竞争失败");
            }
        }
        appSources.findReplica(task.repositoryId(), task.generation(), task.linuxServerId())
                .map(replica -> replica.runtimeWorkspaceId())
                .flatMap(workspaces::findById)
                .ifPresent(workspace -> workspaces.save(new Workspace(
                        workspace.workspaceId(), workspace.name(), workspace.rootPath(), WorkspaceStatus.ARCHIVED,
                        workspace.createdAt(), now, workspace.linuxServerId(), workspace.traceId())));
        appSources.markReplicaCleaned(task.repositoryId(), task.generation(), task.linuxServerId(), now);
        if (!appSources.completeCleanupTask(task.cleanupTaskId(), leaseOwner, now)) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码清理租约已失效");
        }
    }
}
