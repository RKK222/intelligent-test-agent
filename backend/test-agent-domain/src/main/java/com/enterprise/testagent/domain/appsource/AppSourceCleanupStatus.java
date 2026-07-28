package com.enterprise.testagent.domain.appsource;

import java.util.Set;

/** 单服务器延迟清理任务状态。 */
public enum AppSourceCleanupStatus {
    PENDING,
    RUNNING,
    RETRY_WAIT,
    CLEANED,
    SUPERSEDED;

    /** 清理任务只允许认领、重试、完成或被新代次取代。 */
    public boolean canTransitionTo(AppSourceCleanupStatus next) {
        return switch (this) {
            case PENDING -> Set.of(RUNNING, SUPERSEDED).contains(next);
            case RUNNING -> Set.of(RETRY_WAIT, CLEANED, SUPERSEDED).contains(next);
            case RETRY_WAIT -> Set.of(RUNNING, SUPERSEDED).contains(next);
            case CLEANED, SUPERSEDED -> false;
        };
    }
}
