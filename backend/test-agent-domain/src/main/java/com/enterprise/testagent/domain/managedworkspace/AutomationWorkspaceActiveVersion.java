package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 自动化代码库配置当前对应用成员生效的只读版本。 */
public record AutomationWorkspaceActiveVersion(
        ApplicationWorkspaceId applicationWorkspaceId,
        ApplicationWorkspaceVersionId versionId,
        UserId activatedBy,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt) {

    public AutomationWorkspaceActiveVersion {
        Objects.requireNonNull(applicationWorkspaceId, "applicationWorkspaceId must not be null");
        Objects.requireNonNull(versionId, "versionId must not be null");
        Objects.requireNonNull(activatedAt, "activatedAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
