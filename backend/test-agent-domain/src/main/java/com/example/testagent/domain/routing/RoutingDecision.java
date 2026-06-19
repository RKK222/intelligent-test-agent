package com.example.testagent.domain.routing;

import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.support.DomainValidation;
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

    public RoutingDecision {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(executionNodeId, "executionNodeId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        decidedAt = DomainValidation.requireInstant(decidedAt, "decidedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
