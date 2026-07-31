package com.enterprise.testagent.domain.workflowcapability;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 模型网关短期委托；真实供应商Token始终不进入该对象。 */
public record WorkflowModelGrantPayload(
        String grantId,
        String userId,
        String unifiedAuthId,
        String sessionDigest,
        String clientId,
        String taskId,
        String runId,
        List<String> analyzerIds,
        Instant expiresAt) {

    public WorkflowModelGrantPayload {
        Objects.requireNonNull(grantId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(unifiedAuthId);
        Objects.requireNonNull(sessionDigest);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(runId);
        analyzerIds = List.copyOf(analyzerIds);
        Objects.requireNonNull(expiresAt);
    }
}
