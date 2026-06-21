package com.icbc.testagent.domain.routing;

import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 执行路由决策值对象，记录 run、节点、原因和 traceId，供后续持久化和审计使用。
 */
public record RoutingDecision(
        RunId runId,
        ExecutionNodeId executionNodeId,
        RoutingReason reason,
        Instant decidedAt,
        String traceId) {

    /**
     * 校验路由决策必填字段，确保审计记录可以完整关联 run、节点和 traceId。
     */
    public RoutingDecision {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(executionNodeId, "executionNodeId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        decidedAt = DomainValidation.requireInstant(decidedAt, "decidedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
