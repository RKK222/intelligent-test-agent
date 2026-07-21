package com.enterprise.testagent.opencode.runtime.night;

/** 内部批量分发的逐任务结果；STARTED/ALREADY_STARTED 才表示分发成功。 */
public enum NightExecutionDispatchStatus {
    STARTED,
    ALREADY_STARTED,
    IN_PROGRESS,
    NOT_FOUND,
    NOT_DUE,
    TARGET_MISMATCH,
    TERMINAL,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
}
