package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * 用户工作区最近 VCS 分支偏好；按 (userId, appId, workspaceId) 维度保存最近一次手动选择的 Git 分支，
 * 用于下次进入同一工作区时把分支显示在 IDE 上，方便用户快速恢复上下文。
 */
public record UserWorkspaceBranchPreference(
        UserId userId,
        ApplicationId appId,
        WorkspaceId workspaceId,
        String branch,
        Instant updatedAt) {

    public UserWorkspaceBranchPreference {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        branch = DomainValidation.requireText(branch, "branch");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
    }
}
