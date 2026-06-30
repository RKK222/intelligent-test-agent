package com.icbc.testagent.domain.managedworkspace;

import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 托管工作区持久化端口，屏蔽版本、个人空间、最近使用和同步审计的 JDBC 实现。
 */
public interface ManagedWorkspaceRepository {

    List<ApplicationWorkspaceVersion> findVersions(ApplicationWorkspaceId applicationWorkspaceId);

    List<ApplicationWorkspaceVersion> findVersionsByApplication(ApplicationId appId);

    Optional<ApplicationWorkspaceVersion> findVersion(ApplicationWorkspaceVersionId versionId);

    Optional<ApplicationWorkspaceVersion> findVersionByTemplateAndVersion(ApplicationWorkspaceId applicationWorkspaceId, String version);

    ApplicationWorkspaceVersion saveVersion(ApplicationWorkspaceVersion version);

    ApplicationWorkspaceVersion updateVersionTargetCommit(
            ApplicationWorkspaceVersionId versionId,
            String targetCommitHash,
            Instant updatedAt);

    ApplicationWorkspaceVersionReplica saveVersionReplica(ApplicationWorkspaceVersionReplica replica);

    Optional<ApplicationWorkspaceVersionReplica> findVersionReplica(
            ApplicationWorkspaceVersionId versionId,
            String linuxServerId);

    Optional<ApplicationWorkspaceVersionReplica> findVersionReplicaByRuntimeWorkspace(WorkspaceId workspaceId);

    List<ApplicationWorkspaceVersion> findActiveVersionsMissingReadyReplica(String linuxServerId);

    List<PersonalWorkspace> findPersonalWorkspaces(ApplicationWorkspaceVersionId versionId, UserId userId);

    Optional<PersonalWorkspace> findPersonalWorkspace(PersonalWorkspaceId personalWorkspaceId);

    Optional<PersonalWorkspace> findPersonalWorkspaceByRuntimeWorkspace(WorkspaceId workspaceId);

    PersonalWorkspace savePersonalWorkspace(PersonalWorkspace workspace);

    Optional<ApplicationWorkspaceVersion> findVersionByRuntimeWorkspace(WorkspaceId workspaceId);

    void savePreference(UserWorkspacePreference preference);

    Optional<UserWorkspacePreference> findGlobalPreference(UserId userId);

    Optional<UserWorkspacePreference> findApplicationPreference(UserId userId, ApplicationId appId);

    /**
     * 写入或更新 (userId, appId, workspaceId) 维度的最近 VCS 分支偏好。
     */
    void saveBranchPreference(UserWorkspaceBranchPreference preference);

    /**
     * 查找 (userId, appId, workspaceId) 维度的最近 VCS 分支偏好；未设置时返回 Optional.empty。
     */
    Optional<UserWorkspaceBranchPreference> findBranchPreference(UserId userId, ApplicationId appId, WorkspaceId workspaceId);

    void saveSyncRecord(WorkspaceSyncRecord record);

    /**
     * 级联删除指定应用工作空间关联的所有子表数据（同步记录 → 版本副本 → 个人工作空间 → 应用版本工作空间）。
     * 调用方需在之后自行删除 application_workspaces 主表记录。
     */
    void deleteAllByApplicationWorkspaceId(ApplicationWorkspaceId applicationWorkspaceId);
}
