package com.icbc.testagent.domain.routing;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 执行节点路由器只处理纯决策：从候选节点中选出可承载 Run 的节点，不读取数据库或调用 opencode。
 */
public final class ExecutionNodeRouter {

    /**
     * 从候选节点中选出可接收 run 的节点；无可用节点时抛出 opencode 不可用错误。
     */
    public RoutingDecision route(RunId runId, List<ExecutionNode> candidates, Instant decidedAt, String traceId) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");
        decidedAt = DomainValidation.requireInstant(decidedAt, "decidedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");

        ExecutionNode selected = candidates.stream()
                .filter(ExecutionNode::canAcceptRun)
                .min(Comparator.comparingInt(ExecutionNode::runningRuns)
                        .thenComparing(Comparator.comparingInt(ExecutionNode::weight).reversed())
                        .thenComparing(ExecutionNode::updatedAt))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "没有可用的 opencode 执行节点",
                        Map.of("runId", runId.value(), "candidateCount", candidates.size())));

        return new RoutingDecision(
                runId,
                selected.executionNodeId(),
                RoutingReason.LOWEST_LOAD,
                decidedAt,
                traceId);
    }
}
