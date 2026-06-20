package com.example.testagent.domain.node;

import com.example.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Set;
import java.util.Objects;

/**
 * 执行节点领域对象，只表达路由所需的节点状态和容量，不直接访问 opencode server。
 */
public record ExecutionNode(
        ExecutionNodeId executionNodeId,
        String baseUrl,
        ExecutionNodeStatus status,
        int runningRuns,
        int maxRuns,
        int weight,
        Instant lastHeartbeatAt,
        Set<String> capabilities,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 构造基础执行节点，使用默认权重、空能力集合和占位 traceId，主要用于测试和简单 seed 场景。
     */
    public ExecutionNode(
            ExecutionNodeId executionNodeId,
            String baseUrl,
            ExecutionNodeStatus status,
            int runningRuns,
            int maxRuns,
            Instant updatedAt) {
        this(
                executionNodeId,
                baseUrl,
                status,
                runningRuns,
                maxRuns,
                100,
                updatedAt,
                Set.of(),
                updatedAt,
                updatedAt,
                "trace_unspecified");
    }

    /**
     * 校验执行节点路由所需不变量；容量不能为负或超额，能力集合会复制成不可变集合。
     */
    public ExecutionNode {
        Objects.requireNonNull(executionNodeId, "executionNodeId must not be null");
        baseUrl = DomainValidation.requireText(baseUrl, "baseUrl");
        Objects.requireNonNull(status, "status must not be null");
        if (runningRuns < 0) {
            throw new IllegalArgumentException("runningRuns must be greater than or equal to 0");
        }
        if (maxRuns < 1) {
            throw new IllegalArgumentException("maxRuns must be greater than or equal to 1");
        }
        if (runningRuns > maxRuns) {
            throw new IllegalArgumentException("runningRuns must not exceed maxRuns");
        }
        if (weight < 0) {
            throw new IllegalArgumentException("weight must be greater than or equal to 0");
        }
        lastHeartbeatAt = DomainValidation.requireInstant(lastHeartbeatAt, "lastHeartbeatAt");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /**
     * 返回节点当前剩余可接收 Run 数量，调用方可用于路由排序和健康展示。
     */
    public int availableCapacity() {
        return maxRuns - runningRuns;
    }

    /**
     * 判断节点是否可接收新的 Run；只有 READY 且仍有容量时才允许路由。
     */
    public boolean canAcceptRun() {
        return status == ExecutionNodeStatus.READY && availableCapacity() > 0;
    }
}
