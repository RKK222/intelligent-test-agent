package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import java.time.Instant;
import java.util.Objects;

/**
 * 设置页创建应用工作空间的一次进度快照，供前端轮询展示后端当前步骤。
 */
public record WorkspaceCreateOperation(
        String operationId,
        ApplicationId appId,
        UserId requestedBy,
        WorkspaceCreateOperationStatus status,
        WorkspaceCreateOperationStep currentStep,
        String errorCode,
        String errorMessage,
        ApplicationWorkspaceId workspaceId,
        ApplicationWorkspaceVersionId versionId,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    public WorkspaceCreateOperation {
        operationId = DomainValidation.requireText(operationId, "operationId").trim();
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(currentStep, "currentStep must not be null");
        errorCode = normalizeOptional(errorCode);
        errorMessage = normalizeOptional(errorMessage);
        traceId = DomainValidation.requireText(traceId, "traceId").trim();
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
