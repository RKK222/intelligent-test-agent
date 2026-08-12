package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/** 平台工作区与本地实例根目录的稳定绑定；根路径仅以摘要和文件系统身份保存于此聚合。 */
public record LocalClientWorkspaceBinding(
        WorkspaceId workspaceId,
        UserId userId,
        LocalClientInstanceId clientInstanceId,
        String normalizedRootPath,
        String rootDigest,
        String fileSystemIdentity,
        Instant createdAt,
        Instant updatedAt) {

    public LocalClientWorkspaceBinding {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(clientInstanceId, "clientInstanceId must not be null");
        normalizedRootPath = DomainValidation.requireText(normalizedRootPath, "normalizedRootPath");
        rootDigest = DomainValidation.requireText(rootDigest, "rootDigest");
        fileSystemIdentity = DomainValidation.requireText(fileSystemIdentity, "fileSystemIdentity");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
    }
}
