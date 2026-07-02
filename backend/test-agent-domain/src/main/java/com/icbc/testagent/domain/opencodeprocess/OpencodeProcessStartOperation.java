package com.icbc.testagent.domain.opencodeprocess;

import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 当前用户 opencode 进程启动进度快照，供浏览器轮询展示公共启动链路状态。
 */
public record OpencodeProcessStartOperation(
        String operationId,
        UserId requestedBy,
        String agentId,
        OpencodeProcessStartOperationStatus status,
        OpencodeProcessStartOperationStep currentStep,
        String errorCode,
        String errorMessage,
        String processId,
        String serviceAddress,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    public OpencodeProcessStartOperation {
        operationId = DomainValidation.requireText(operationId, "operationId").trim();
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        agentId = DomainValidation.requireText(agentId, "agentId").trim();
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(currentStep, "currentStep must not be null");
        errorCode = optionalText(errorCode);
        errorMessage = optionalText(errorMessage);
        processId = optionalText(processId);
        serviceAddress = optionalText(serviceAddress);
        traceId = DomainValidation.requireText(traceId, "traceId").trim();
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public static OpencodeProcessStartOperation started(
            String operationId,
            UserId requestedBy,
            String agentId,
            String traceId,
            Instant now) {
        return new OpencodeProcessStartOperation(
                operationId,
                requestedBy,
                agentId,
                OpencodeProcessStartOperationStatus.RUNNING,
                OpencodeProcessStartOperationStep.VALIDATING_REQUEST,
                null,
                null,
                null,
                null,
                traceId,
                now,
                now);
    }

    public OpencodeProcessStartOperation step(OpencodeProcessStartOperationStep step, Instant now) {
        return new OpencodeProcessStartOperation(
                operationId,
                requestedBy,
                agentId,
                OpencodeProcessStartOperationStatus.RUNNING,
                step,
                null,
                null,
                processId,
                serviceAddress,
                traceId,
                createdAt,
                now);
    }

    public OpencodeProcessStartOperation succeeded(String processId, String serviceAddress, Instant now) {
        return new OpencodeProcessStartOperation(
                operationId,
                requestedBy,
                agentId,
                OpencodeProcessStartOperationStatus.SUCCEEDED,
                OpencodeProcessStartOperationStep.COMPLETED,
                null,
                null,
                processId,
                serviceAddress,
                traceId,
                createdAt,
                now);
    }

    public OpencodeProcessStartOperation failed(
            OpencodeProcessStartOperationStep step,
            String errorCode,
            String errorMessage,
            Instant now) {
        return new OpencodeProcessStartOperation(
                operationId,
                requestedBy,
                agentId,
                OpencodeProcessStartOperationStatus.FAILED,
                step == null ? currentStep : step,
                errorCode,
                errorMessage,
                processId,
                serviceAddress,
                traceId,
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
