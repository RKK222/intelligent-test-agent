package com.enterprise.testagent.domain.scheduler;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 定时任务框架持久化端口，具体 JDBC/Redis 实现由基础设施模块提供。
 */
public interface ScheduledTaskRepository {

    ScheduledTask saveTask(ScheduledTask task);

    Optional<ScheduledTask> findTaskByKey(ScheduledTaskKey taskKey);

    PageResponse<ScheduledTask> findTasks(PageRequest pageRequest);

    List<ScheduledTask> findDueTasks(Instant now, int limit);

    ScheduledTaskPlan savePlan(ScheduledTaskPlan plan);

    Optional<ScheduledTaskPlan> findPlanById(ScheduledTaskPlanId planId);

    ScheduledTaskRun saveRun(ScheduledTaskRun run);

    Optional<ScheduledTaskRun> findRunById(ScheduledTaskRunId taskRunId);

    Optional<ScheduledTaskRun> findActiveRunByTaskKey(ScheduledTaskKey taskKey);

    Optional<ScheduledTaskRun> findActiveRunByTaskKeyExcluding(ScheduledTaskKey taskKey, ScheduledTaskRunId taskRunId);

    List<ScheduledTaskRun> findPendingRuns(ScheduledTaskTriggerType triggerType, Instant now, int limit);

    PageResponse<ScheduledTaskRun> findRuns(ScheduledTaskRunFilter filter, PageRequest pageRequest);
}
