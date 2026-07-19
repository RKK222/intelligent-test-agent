package com.enterprise.testagent.domain.nightexecution;

/** 夜间任务只跟踪投递到 Run 之前的生命周期。 */
public enum NightExecutionTaskStatus {
    SCHEDULED,
    DISPATCHING,
    DISPATCHED,
    CANCELLED,
    FAILED;

    public boolean pending() {
        return this == SCHEDULED || this == DISPATCHING;
    }
}
