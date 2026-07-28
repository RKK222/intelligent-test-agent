package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 用户请求对应的全局源码操作；requestHash 用于业务层幂等判断。 */
public record AppSourceOperation(
        String operationId,
        ApplicationId appId,
        CodeRepositoryId repositoryId,
        Long sourceGeneration,
        long targetGeneration,
        UserId actorUserId,
        AppSourceOperationType operationType,
        String requestHash,
        AppSourceOperationStatus status,
        String traceId,
        Instant acceptedAt,
        Instant completedAt) {

    public AppSourceOperation {
        operationId = requireText(operationId, "operationId");
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        if ((sourceGeneration != null && sourceGeneration < 1L) || targetGeneration < 1L) {
            throw new IllegalArgumentException("generations must be positive");
        }
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        Objects.requireNonNull(operationType, "operationType must not be null");
        requestHash = requireText(requestHash, "requestHash");
        Objects.requireNonNull(status, "status must not be null");
        traceId = requireText(traceId, "traceId");
        acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        if (status.terminal() != (completedAt != null)) {
            throw new IllegalArgumentException("completedAt must exist exactly for a terminal operation");
        }
        if (completedAt != null && completedAt.isBefore(acceptedAt)) {
            throw new IllegalArgumentException("completedAt must not be before acceptedAt");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
