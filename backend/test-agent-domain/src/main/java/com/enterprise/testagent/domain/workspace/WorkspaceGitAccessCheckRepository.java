package com.enterprise.testagent.domain.workspace;

import com.enterprise.testagent.domain.user.UserId;
import java.util.List;
import java.util.Optional;

/** 工作空间 Git 权限巡检候选与结果持久化端口。 */
public interface WorkspaceGitAccessCheckRepository {

    List<ApplicationWorkspaceCandidate> findApplicationWorkspaceCandidatesAfter(
            String afterUserId,
            String afterWorkspaceId,
            int limit);

    List<LocalWorkspaceCandidate> findLocalWorkspaceCandidatesAfter(
            String afterUserId,
            String afterWorkspaceId,
            int limit);

    Optional<WorkspaceGitAccessCheck> find(
            UserId userId,
            WorkspaceGitAccessCheck.TargetKind targetKind,
            String targetId);

    void save(WorkspaceGitAccessCheck check);

    /** 服务器托管工作空间复用任一 ACTIVE 版本执行现有版本库只读预检。 */
    record ApplicationWorkspaceCandidate(
            UserId userId,
            String applicationWorkspaceId,
            String versionId) {
    }

    /** 本地工作空间巡检只携带反向 RPC 所需的稳定逻辑坐标，不暴露绝对路径。 */
    record LocalWorkspaceCandidate(
            UserId userId,
            WorkspaceId workspaceId,
            String clientInstanceId,
            String rootDigest) {
    }
}
