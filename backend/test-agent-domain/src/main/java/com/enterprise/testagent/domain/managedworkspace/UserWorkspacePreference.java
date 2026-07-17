package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * 用户最近使用工作区偏好；appId 为空表示用户全局最近使用。
 */
public record UserWorkspacePreference(
        UserId userId,
        ApplicationId appId,
        WorkspaceId workspaceId,
        Instant updatedAt) {

    public UserWorkspacePreference {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
    }
}
