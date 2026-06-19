package com.example.testagent.domain.run;

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

    public boolean canTransitionTo(RunStatus next) {
        if (next == null || this == next || isTerminal()) {
            return false;
        }
        return allowedTransitions().contains(next);
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    private Set<RunStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(RUNNING, CANCELLED, FAILED);
            case RUNNING -> EnumSet.of(CANCELLING, SUCCEEDED, FAILED);
            case CANCELLING -> EnumSet.of(CANCELLED, FAILED);
            case SUCCEEDED, FAILED, CANCELLED -> EnumSet.noneOf(RunStatus.class);
        };
    }
}
