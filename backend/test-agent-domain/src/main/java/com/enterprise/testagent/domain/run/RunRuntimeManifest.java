package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * Redis 中单个 Run 的可信运行态清单；路由、恢复和 active 索引都必须回读本清单二次确认。
 */
public record RunRuntimeManifest(
        RunId runId,
        RunStorageMode storageMode,
        UserId userId,
        UserId messageSenderUserId,
        String messageSenderUnifiedAuthId,
        boolean messageSentBySharedUser,
        SessionId sessionId,
        WorkspaceId workspaceId,
        String agentId,
        String clientRequestId,
        String dispatchMessageId,
        String producerLinuxServerId,
        String backendProcessId,
        String executionNodeId,
        String opencodeProcessId,
        String rootRemoteSessionId,
        RunStatus status,
        long statusVersion,
        long lastSeq,
        long earliestSeq,
        long resetGeneration,
        boolean detailsTruncated,
        long durableEventCount,
        long detailBytes,
        String attention,
        String attentionEventId,
        Instant attentionAt,
        Instant detailsExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        RuntimeKind targetRuntimeKind,
        String targetLocalClientInstanceId,
        Long targetConnectionGeneration) {

    /** 兼容本地运行目标加入前、已包含分享发送人归因的完整构造器。 */
    public RunRuntimeManifest(
            RunId runId,
            RunStorageMode storageMode,
            UserId userId,
            UserId messageSenderUserId,
            String messageSenderUnifiedAuthId,
            boolean messageSentBySharedUser,
            SessionId sessionId,
            WorkspaceId workspaceId,
            String agentId,
            String clientRequestId,
            String dispatchMessageId,
            String producerLinuxServerId,
            String backendProcessId,
            String executionNodeId,
            String opencodeProcessId,
            String rootRemoteSessionId,
            RunStatus status,
            long statusVersion,
            long lastSeq,
            long earliestSeq,
            long resetGeneration,
            boolean detailsTruncated,
            long durableEventCount,
            long detailBytes,
            String attention,
            String attentionEventId,
            Instant attentionAt,
            Instant detailsExpiresAt,
            Instant createdAt,
            Instant updatedAt) {
        this(runId, storageMode, userId, messageSenderUserId, messageSenderUnifiedAuthId,
                messageSentBySharedUser, sessionId, workspaceId, agentId, clientRequestId,
                dispatchMessageId, producerLinuxServerId, backendProcessId, executionNodeId,
                opencodeProcessId, rootRemoteSessionId, status, statusVersion, lastSeq, earliestSeq,
                resetGeneration, detailsTruncated, durableEventCount, detailBytes, attention,
                attentionEventId, attentionAt, detailsExpiresAt, createdAt, updatedAt,
                RuntimeKind.SERVER_PROCESS, null, null);
    }

    /** 兼容新增分享发送人归因前的完整构造器。 */
    public RunRuntimeManifest(
            RunId runId,
            RunStorageMode storageMode,
            UserId userId,
            SessionId sessionId,
            WorkspaceId workspaceId,
            String agentId,
            String clientRequestId,
            String dispatchMessageId,
            String producerLinuxServerId,
            String backendProcessId,
            String executionNodeId,
            String opencodeProcessId,
            String rootRemoteSessionId,
            RunStatus status,
            long statusVersion,
            long lastSeq,
            long earliestSeq,
            long resetGeneration,
            boolean detailsTruncated,
            long durableEventCount,
            long detailBytes,
            String attention,
            String attentionEventId,
            Instant attentionAt,
            Instant detailsExpiresAt,
            Instant createdAt,
            Instant updatedAt) {
        this(runId, storageMode, userId, userId, null, false, sessionId, workspaceId,
                agentId, clientRequestId, dispatchMessageId, producerLinuxServerId,
                backendProcessId, executionNodeId, opencodeProcessId, rootRemoteSessionId,
                status, statusVersion, lastSeq, earliestSeq, resetGeneration, detailsTruncated,
                durableEventCount, detailBytes, attention, attentionEventId, attentionAt,
                detailsExpiresAt, createdAt, updatedAt, RuntimeKind.SERVER_PROCESS, null, null);
    }

    public RunRuntimeManifest {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(storageMode, "storageMode must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        agentId = requireText(agentId, "agentId");
        producerLinuxServerId = requireText(producerLinuxServerId, "producerLinuxServerId");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(detailsExpiresAt, "detailsExpiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        targetRuntimeKind = RuntimeKind.fromNullable(targetRuntimeKind);
        targetLocalClientInstanceId = normalizeOptional(targetLocalClientInstanceId);
        if (targetRuntimeKind == RuntimeKind.LOCAL_CLIENT) {
            if (targetLocalClientInstanceId == null
                    || targetConnectionGeneration == null
                    || targetConnectionGeneration < 1) {
                throw new IllegalArgumentException("local run manifest requires client instance and generation");
            }
        } else if (targetLocalClientInstanceId != null || targetConnectionGeneration != null) {
            throw new IllegalArgumentException("server run manifest must not carry local client target");
        }
        if (statusVersion < 0 || lastSeq < 0 || earliestSeq < 0 || resetGeneration < 0
                || durableEventCount < 0 || detailBytes < 0) {
            throw new IllegalArgumentException("runtime counters must not be negative");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /** 返回当前 Run 是否仍应出现在 active 索引。 */
    public boolean active() {
        return !status.isTerminal();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
