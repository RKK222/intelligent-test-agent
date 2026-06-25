package com.icbc.testagent.domain.run;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Run 聚合根，封装运行状态迁移规则；持久化和执行节点选择由后续阶段适配。
 */
public record Run(
        RunId runId,
        SessionId sessionId,
        WorkspaceId workspaceId,
        RunStatus status,
        Instant createdAt,
        Instant updatedAt,
        String traceId,
        TokenUsage tokenUsage,
        BigDecimal costUsd) {

    /**
     * 构造未指定 traceId 的 Run，兼容历史测试和持久化重建路径，内部使用占位 traceId。
     */
    public Run(
            RunId runId,
            SessionId sessionId,
            WorkspaceId workspaceId,
            RunStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this(runId, sessionId, workspaceId, status, createdAt, updatedAt, "trace_unspecified");
    }

    /**
     * 构造不含 token/cost 的 Run，兼容既有编排和测试代码。
     */
    public Run(
            RunId runId,
            SessionId sessionId,
            WorkspaceId workspaceId,
            RunStatus status,
            Instant createdAt,
            Instant updatedAt,
            String traceId) {
        this(runId, sessionId, workspaceId, status, createdAt, updatedAt, traceId, TokenUsage.empty(), null);
    }

    /**
     * 校验 Run 聚合的必填字段、时间顺序和可选消耗字段，确保领域对象不会表达无效运行状态。
     */
    public Run {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        traceId = com.icbc.testagent.domain.support.DomainValidation.requireText(traceId, "traceId");
        tokenUsage = tokenUsage == null ? TokenUsage.empty() : tokenUsage;
        if (costUsd != null && costUsd.signum() < 0) {
            throw new IllegalArgumentException("costUsd must not be negative");
        }
    }

    /**
     * 按 Run 状态机执行一次状态流转；非法流转抛出平台冲突错误并携带当前状态和目标状态。
     */
    public Run transitionTo(RunStatus nextStatus, Instant nextUpdatedAt) {
        Objects.requireNonNull(nextStatus, "nextStatus must not be null");
        Objects.requireNonNull(nextUpdatedAt, "nextUpdatedAt must not be null");
        if (!status.canTransitionTo(nextStatus)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Run 状态不允许从 " + status + " 流转到 " + nextStatus,
                    Map.of("currentStatus", status.name(), "nextStatus", nextStatus.name()));
        }
        return new Run(runId, sessionId, workspaceId, nextStatus, createdAt, nextUpdatedAt, traceId, tokenUsage, costUsd);
    }

    /**
     * 将待运行 Run 标记为运行中；只允许从 PENDING 状态进入。
     */
    public Run start(Instant nextUpdatedAt) {
        return transitionTo(RunStatus.RUNNING, nextUpdatedAt);
    }

    /**
     * 处理取消请求；PENDING Run 直接终止，RUNNING Run 先进入 CANCELLING 等待远端确认。
     */
    public Run requestCancel(Instant nextUpdatedAt) {
        if (status == RunStatus.PENDING) {
            return transitionTo(RunStatus.CANCELLED, nextUpdatedAt);
        }
        return transitionTo(RunStatus.CANCELLING, nextUpdatedAt);
    }

    /**
     * 将运行中 Run 标记为成功终态，通常由 opencode idle/step ended 事件触发。
     */
    public Run succeed(Instant nextUpdatedAt) {
        return transitionTo(RunStatus.SUCCEEDED, nextUpdatedAt);
    }

    /**
     * 将 Run 标记为失败终态，用于远端异常、本地编排错误或取消失败后的收敛。
     */
    public Run fail(Instant nextUpdatedAt) {
        return transitionTo(RunStatus.FAILED, nextUpdatedAt);
    }

    /**
     * 将正在取消的 Run 标记为取消终态，通常由远端取消确认或本地 pending 取消触发。
     */
    public Run cancel(Instant nextUpdatedAt) {
        return transitionTo(RunStatus.CANCELLED, nextUpdatedAt);
    }

    /**
     * 更新本次 Run 的 token/cost 快照，供会话列表和消息快照展示每次对话消耗。
     */
    public Run withUsage(TokenUsage tokenUsage, BigDecimal costUsd) {
        return new Run(runId, sessionId, workspaceId, status, createdAt, updatedAt, traceId, tokenUsage, costUsd);
    }
}
