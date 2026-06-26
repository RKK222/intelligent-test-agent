package com.icbc.testagent.domain.configuration;

import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * Agent 配置 Git 操作进度快照，浏览器通过 operationId 订阅 WebSocket 进度。
 */
public record AgentConfigOperation(
        String operationId,
        AgentConfigScope scope,
        WorkspaceId workspaceId,
        String action,
        AgentConfigOperationStatus status,
        AgentConfigOperationStep currentStep,
        String errorCode,
        String errorMessage,
        String traceId,
        String branch,
        String commitHash,
        Instant createdAt,
        Instant updatedAt) {

    public AgentConfigOperation {
        operationId = DomainValidation.requireText(operationId, "operationId").trim();
        Objects.requireNonNull(scope, "scope must not be null");
        action = DomainValidation.requireText(action, "action").trim();
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(currentStep, "currentStep must not be null");
        errorCode = optionalText(errorCode);
        errorMessage = optionalText(errorMessage);
        traceId = DomainValidation.requireText(traceId, "traceId").trim();
        branch = optionalText(branch);
        commitHash = optionalText(commitHash);
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public static AgentConfigOperation started(
            String operationId,
            AgentConfigScope scope,
            WorkspaceId workspaceId,
            String action,
            String traceId,
            String branch,
            Instant now) {
        return new AgentConfigOperation(
                operationId,
                scope,
                workspaceId,
                action,
                AgentConfigOperationStatus.RUNNING,
                AgentConfigOperationStep.VALIDATING,
                null,
                null,
                traceId,
                branch,
                null,
                now,
                now);
    }

    public AgentConfigOperation step(AgentConfigOperationStep step, Instant now) {
        return new AgentConfigOperation(
                operationId,
                scope,
                workspaceId,
                action,
                AgentConfigOperationStatus.RUNNING,
                step,
                null,
                null,
                traceId,
                branch,
                commitHash,
                createdAt,
                now);
    }

    public AgentConfigOperation succeeded(String commitHash, Instant now) {
        return new AgentConfigOperation(
                operationId,
                scope,
                workspaceId,
                action,
                AgentConfigOperationStatus.SUCCEEDED,
                AgentConfigOperationStep.COMPLETED,
                null,
                null,
                traceId,
                branch,
                commitHash,
                createdAt,
                now);
    }

    public AgentConfigOperation failed(String errorCode, String errorMessage, Instant now) {
        return new AgentConfigOperation(
                operationId,
                scope,
                workspaceId,
                action,
                AgentConfigOperationStatus.FAILED,
                currentStep,
                errorCode,
                errorMessage,
                traceId,
                branch,
                commitHash,
                createdAt,
                now);
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
