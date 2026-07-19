package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;

/** 待执行任务分页及按会话恢复的最近失败卡。 */
public record NightExecutionTaskQueryResult(
        PageResponse<NightExecutionTask> pendingTasks,
        NightExecutionTask visibleFailure) {
}
