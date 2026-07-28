package com.enterprise.testagent.domain.appsource;

import java.util.Set;

/** 一次全局应用源码操作的状态。 */
public enum AppSourceOperationStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL_FAILED,
    FAILED;

    /** 操作终态不可回退，避免旧 worker 覆盖完成事实。 */
    public boolean canTransitionTo(AppSourceOperationStatus next) {
        return switch (this) {
            case PENDING -> Set.of(RUNNING, FAILED).contains(next);
            case RUNNING -> Set.of(SUCCEEDED, PARTIAL_FAILED, FAILED).contains(next);
            case SUCCEEDED, PARTIAL_FAILED, FAILED -> false;
        };
    }

    public boolean terminal() {
        return this == SUCCEEDED || this == PARTIAL_FAILED || this == FAILED;
    }
}
