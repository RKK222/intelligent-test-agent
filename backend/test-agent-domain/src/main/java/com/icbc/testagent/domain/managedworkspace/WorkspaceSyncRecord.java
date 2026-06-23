package com.icbc.testagent.domain.managedworkspace;

import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 应用版本工作区与个人工作区同步审计记录。
 */
public record WorkspaceSyncRecord(
        WorkspaceSyncRecordId syncRecordId,
        UserId userId,
        WorkspaceId sourceWorkspaceId,
        WorkspaceId targetWorkspaceId,
        WorkspaceSyncDirection direction,
        List<String> files,
        boolean force,
        WorkspaceSyncStatus status,
        String traceId,
        Instant createdAt) {

    public WorkspaceSyncRecord {
        Objects.requireNonNull(syncRecordId, "syncRecordId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceWorkspaceId, "sourceWorkspaceId must not be null");
        Objects.requireNonNull(targetWorkspaceId, "targetWorkspaceId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        files = files == null ? List.of() : List.copyOf(files);
        Objects.requireNonNull(status, "status must not be null");
        traceId = DomainValidation.requireText(traceId, "traceId");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
    }
}
