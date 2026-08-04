package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.Objects;

/** 数据库实时发现的“个人工作区与用户 Agent 服务器错配”候选。 */
public record PersonalWorkspaceRelocationCandidate(
        PersonalWorkspaceId personalWorkspaceId,
        ApplicationWorkspaceVersionId versionId,
        UserId userId,
        WorkspaceId runtimeWorkspaceId,
        String sourceLinuxServerId,
        String targetLinuxServerId) {

    public PersonalWorkspaceRelocationCandidate {
        Objects.requireNonNull(personalWorkspaceId, "personalWorkspaceId must not be null");
        Objects.requireNonNull(versionId, "versionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(runtimeWorkspaceId, "runtimeWorkspaceId must not be null");
        sourceLinuxServerId = requireText(sourceLinuxServerId, "sourceLinuxServerId");
        targetLinuxServerId = requireText(targetLinuxServerId, "targetLinuxServerId");
        if (sourceLinuxServerId.equals(targetLinuxServerId)) {
            throw new IllegalArgumentException("source and target linux server must differ");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
