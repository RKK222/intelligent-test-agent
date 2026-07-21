package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 普通 Run 写入 PostgreSQL 的无原文幂等锚点。
 *
 * <p>该对象只包含幂等键、路由快照和生命周期元数据，禁止加入 prompt、回答、parts 或原始事件。
 * Redis 摘要模式必须携带完整路由快照；legacy Scheduled Run 只写启动时已经确定的稳定字段。
 * 插入时状态必须为活动态；同一数据库锚点后续会被终态更新，因此查询快照允许终态。
 */
public record RunPersistenceAnchor(
        RunId runId,
        SessionId sessionId,
        WorkspaceId workspaceId,
        RunStatus status,
        RunStorageMode storageMode,
        long statusVersion,
        String clientRequestId,
        String producerLinuxServerId,
        String executionNodeIdSnapshot,
        String opencodeProcessIdSnapshot,
        String rootRemoteSessionId,
        String dispatchMessageId,
        String scheduledDispatchAttemptId,
        Instant scheduledDispatchLeaseUntil,
        Instant scheduledDispatchAcceptedAt,
        SessionMessageId assistantSummaryMessageId,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Instant detailsExpiresAt,
        ConversationSourceType sourceType,
        String sourceRefId,
        UserId triggeredByUserId,
        String agentId,
        String modelId) {

    public RunPersistenceAnchor {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(storageMode, "storageMode must not be null");
        if (statusVersion < 0) {
            throw new IllegalArgumentException("statusVersion must not be negative");
        }
        clientRequestId = requiredBounded(clientRequestId, "clientRequestId", 128);
        if (storageMode == RunStorageMode.REDIS_SUMMARY) {
            producerLinuxServerId = requiredBounded(producerLinuxServerId, "producerLinuxServerId", 128);
            executionNodeIdSnapshot = requiredBounded(executionNodeIdSnapshot, "executionNodeIdSnapshot", 128);
            opencodeProcessIdSnapshot = requiredBounded(opencodeProcessIdSnapshot, "opencodeProcessIdSnapshot", 128);
            Objects.requireNonNull(assistantSummaryMessageId, "assistantSummaryMessageId must not be null");
            detailsExpiresAt = DomainValidation.requireInstant(detailsExpiresAt, "detailsExpiresAt");
        } else {
            producerLinuxServerId = optionalBounded(producerLinuxServerId, "producerLinuxServerId", 128);
            executionNodeIdSnapshot = optionalBounded(
                    executionNodeIdSnapshot, "executionNodeIdSnapshot", 128);
            opencodeProcessIdSnapshot = optionalBounded(
                    opencodeProcessIdSnapshot, "opencodeProcessIdSnapshot", 128);
        }
        rootRemoteSessionId = optionalBounded(rootRemoteSessionId, "rootRemoteSessionId", 128);
        dispatchMessageId = requiredBounded(dispatchMessageId, "dispatchMessageId", 128);
        scheduledDispatchAttemptId = optionalBounded(
                scheduledDispatchAttemptId, "scheduledDispatchAttemptId", 128);
        if (scheduledDispatchLeaseUntil != null) {
            scheduledDispatchLeaseUntil = DomainValidation.requireInstant(
                    scheduledDispatchLeaseUntil, "scheduledDispatchLeaseUntil");
        }
        if (scheduledDispatchAcceptedAt != null) {
            scheduledDispatchAcceptedAt = DomainValidation.requireInstant(
                    scheduledDispatchAcceptedAt, "scheduledDispatchAcceptedAt");
        }
        traceId = requiredBounded(traceId, "traceId", 128);
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (detailsExpiresAt != null && detailsExpiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("detailsExpiresAt must not be before createdAt");
        }
        sourceType = sourceType == null ? ConversationSourceType.MANUAL : sourceType;
        sourceRefId = optionalBounded(sourceRefId, "sourceRefId", 128);
        agentId = requiredBounded(agentId, "agentId", 64).toLowerCase(Locale.ROOT);
        modelId = optionalBounded(modelId, "modelId", 255);
    }

    private static String requiredBounded(String value, String fieldName, int maxLength) {
        String text = DomainValidation.requireText(value, fieldName);
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return text;
    }

    private static String optionalBounded(String value, String fieldName, int maxLength) {
        return value == null ? null : requiredBounded(value, fieldName, maxLength);
    }
}
