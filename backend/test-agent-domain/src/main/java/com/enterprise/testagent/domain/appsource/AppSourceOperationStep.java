package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.time.Instant;
import java.util.Objects;

/** 操作的全局或单服务器步骤；scope 与 linuxServerId 必须一致。 */
public record AppSourceOperationStep(
        String stepId,
        String operationId,
        AppSourceStepScope scope,
        LinuxServerId linuxServerId,
        String stepCode,
        int sequence,
        AppSourceStepStatus status,
        String safeSummary,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {

    public AppSourceOperationStep {
        stepId = requireText(stepId, "stepId");
        operationId = requireText(operationId, "operationId");
        Objects.requireNonNull(scope, "scope must not be null");
        if ((scope == AppSourceStepScope.GLOBAL) != (linuxServerId == null)) {
            throw new IllegalArgumentException("GLOBAL step must omit server and SERVER step must provide server");
        }
        stepCode = requireText(stepCode, "stepCode");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        Objects.requireNonNull(status, "status must not be null");
        safeSummary = optionalText(safeSummary);
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (completedAt != null && startedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
