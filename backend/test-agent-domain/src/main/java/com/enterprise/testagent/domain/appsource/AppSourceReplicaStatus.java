package com.enterprise.testagent.domain.appsource;

import java.util.Set;

/** 单服务器源码副本状态。 */
public enum AppSourceReplicaStatus {
    PENDING,
    RUNNING,
    READY,
    FAILED,
    STALE,
    CLEANUP_PENDING,
    CLEANED;

    /** 校验副本同步、重试、失效与清理的合法前向流转。 */
    public boolean canTransitionTo(AppSourceReplicaStatus next) {
        return switch (this) {
            case PENDING -> Set.of(RUNNING, FAILED, CLEANUP_PENDING).contains(next);
            case RUNNING -> Set.of(READY, FAILED, CLEANUP_PENDING).contains(next);
            case READY -> Set.of(STALE, CLEANUP_PENDING).contains(next);
            // 重试认领会在一条带 generation/lease 条件的 SQL 中直接进入 RUNNING。
            case FAILED, STALE -> Set.of(PENDING, RUNNING, CLEANUP_PENDING).contains(next);
            case CLEANUP_PENDING -> next == CLEANED;
            case CLEANED -> false;
        };
    }
}
