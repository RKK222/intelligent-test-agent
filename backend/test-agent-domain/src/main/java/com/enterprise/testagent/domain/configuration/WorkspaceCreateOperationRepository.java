package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * 应用工作空间创建进度端口，写入方和查询方通过 operationId 共享状态。
 */
public interface WorkspaceCreateOperationRepository {

    WorkspaceCreateOperation start(
            String operationId,
            ApplicationId appId,
            UserId requestedBy,
            String traceId,
            Instant now);

    WorkspaceCreateOperation markStep(String operationId, WorkspaceCreateOperationStep step, Instant now);

    WorkspaceCreateOperation markSucceeded(
            String operationId,
            ApplicationWorkspaceId workspaceId,
            ApplicationWorkspaceVersionId versionId,
            Instant now);

    WorkspaceCreateOperation markFailed(
            String operationId,
            String errorCode,
            String errorMessage,
            Instant now);

    Optional<WorkspaceCreateOperation> findById(String operationId);
}
