package com.icbc.testagent.domain.run;

import java.util.EnumSet;
import java.util.Set;

/**
 * Run 状态机定义。状态迁移在 domain 内收敛，避免后续 Controller 或 Repository 各自判断。
 */
public enum RunStatus {
    PENDING,
    RUNNING,
    CANCELLING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    /**
     * 判断当前状态是否允许流转到目标状态。
     */
    public boolean canTransitionTo(RunStatus next) {
        if (next == null || this == next || isTerminal()) {
            return false;
        }
        return allowedTransitions().contains(next);
    }

    /**
     * 判断状态是否为终态，终态不允许继续流转。
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    /**
     * 返回当前状态允许的下一状态集合。
     */
    private Set<RunStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(RUNNING, CANCELLED, FAILED);
            case RUNNING -> EnumSet.of(CANCELLING, SUCCEEDED, FAILED);
            case CANCELLING -> EnumSet.of(CANCELLED, FAILED);
            case SUCCEEDED, FAILED, CANCELLED -> EnumSet.noneOf(RunStatus.class);
        };
    }
}
