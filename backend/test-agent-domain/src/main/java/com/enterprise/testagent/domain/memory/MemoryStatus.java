package com.enterprise.testagent.domain.memory;

/** 记忆治理状态，只有 ACTIVE 状态允许进入运行时检索。 */
public enum MemoryStatus {
    CANDIDATE,
    PENDING_CONFIRMATION,
    ACTIVE,
    PAUSED,
    CONFLICTED,
    REJECTED,
    ARCHIVED,
    SUPERSEDED;

    public boolean retrievable() {
        return this == ACTIVE;
    }
}
