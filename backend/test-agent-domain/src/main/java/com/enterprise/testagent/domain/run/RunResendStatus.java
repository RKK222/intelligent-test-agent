package com.enterprise.testagent.domain.run;

/** 原生撤销重发状态；REVERTED 之后禁止回到旧上下文。 */
public enum RunResendStatus {
    WAITING,
    REVERTING,
    REVERTED,
    DISPATCHED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == DISPATCHED || this == FAILED || this == CANCELLED;
    }
}
