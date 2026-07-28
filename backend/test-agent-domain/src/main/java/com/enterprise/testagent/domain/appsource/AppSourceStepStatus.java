package com.enterprise.testagent.domain.appsource;

import java.util.Set;

/** 全局或单服务器操作步骤状态。 */
public enum AppSourceStepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED;

    /** 步骤完成后不可被过期执行者重新置为运行中。 */
    public boolean canTransitionTo(AppSourceStepStatus next) {
        return switch (this) {
            case PENDING -> Set.of(RUNNING, FAILED, SKIPPED).contains(next);
            case RUNNING -> Set.of(SUCCEEDED, FAILED, SKIPPED).contains(next);
            case SUCCEEDED, FAILED, SKIPPED -> false;
        };
    }
}
