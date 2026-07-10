package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * Redis 中单个 Run 的可信运行态清单；路由、恢复和 active 索引都必须回读本清单二次确认。
 */
public record RunRuntimeManifest(
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
}
