package com.enterprise.testagent.domain.appsource;

import java.util.Set;

/** 不可变源码快照的生命周期状态。 */
public enum AppSourceSnapshotStatus {
    PENDING,
    ACTIVE,
    FAILED,
    EXPIRED,
    CLEANED;

    /** 校验快照只沿物化、过期和清理方向前进。 */
    public boolean canTransitionTo(AppSourceSnapshotStatus next) {
        return switch (this) {
            case PENDING -> Set.of(ACTIVE, FAILED, EXPIRED).contains(next);
            case ACTIVE -> next == EXPIRED;
            case FAILED, EXPIRED -> next == CLEANED;
            case CLEANED -> false;
        };
    }
}
