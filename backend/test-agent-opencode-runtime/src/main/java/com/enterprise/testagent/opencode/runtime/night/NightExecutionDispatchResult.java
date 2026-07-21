package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;

/** 不包含 prompt 或附件的内部单任务分发结果。 */
public record NightExecutionDispatchResult(
        NightExecutionTaskId taskId,
        NightExecutionDispatchStatus status,
        String runId,
        String errorCode) {

    public boolean successful() {
        return status == NightExecutionDispatchStatus.STARTED
                || status == NightExecutionDispatchStatus.ALREADY_STARTED;
    }
}
