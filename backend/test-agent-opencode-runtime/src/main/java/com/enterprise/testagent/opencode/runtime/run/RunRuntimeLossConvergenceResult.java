package com.enterprise.testagent.opencode.runtime.run;

import java.util.Objects;

/** Redis 持续中断收敛结果；不携带异常消息或任何会话原文。 */
public record RunRuntimeLossConvergenceResult(
        Outcome outcome,
        boolean remoteCancellationAttempted,
        boolean remoteStopConfirmed) {

    /** 保证“已确认远端停止”不会在未尝试取消时出现。 */
    public RunRuntimeLossConvergenceResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (remoteStopConfirmed && !remoteCancellationAttempted) {
            throw new IllegalArgumentException("remote stop confirmation requires a cancellation attempt");
        }
    }

    /** 收敛分支，调用方可据此决定结束计时器、记录告警或等待 DB 重试链路。 */
    public enum Outcome {
        /** 30 秒窗口内 Redis manifest 已恢复，本次不取消远端也不写终态。 */
        RUNTIME_RECOVERED,
        /** FAILED 终态事务已落库。 */
        TERMINAL_APPLIED,
        /** 关系型锚点版本已经变化，本次事务未覆盖新状态。 */
        TERMINAL_VERSION_CONFLICT,
        /** PostgreSQL 不可用，安全终态投影已进入 Redis 退避重试。 */
        TERMINAL_PENDING_DB,
        /** 关系型终态事务和 Redis 安全重试入队均失败，只能告警等待外部恢复。 */
        TERMINAL_PERSISTENCE_FAILED
    }
}
