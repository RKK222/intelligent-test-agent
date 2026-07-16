package com.enterprise.testagent.scheduler;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.scheduler.ScheduledTask;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskPlan;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskPlanId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRegistrationStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRepository;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRun;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunFilter;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * scheduler 单元测试使用的内存 Repository，模拟框架依赖的查询语义。
 */
final class InMemoryScheduledTaskRepository implements ScheduledTaskRepository {

    private final Map<ScheduledTaskKey, ScheduledTask> tasks = new LinkedHashMap<>();
    private final Map<ScheduledTaskPlanId, ScheduledTaskPlan> plans = new LinkedHashMap<>();
    private final Map<ScheduledTaskRunId, ScheduledTaskRun> runs = new LinkedHashMap<>();

    @Override
    public ScheduledTask saveTask(ScheduledTask task) {
        tasks.put(task.taskKey(), task);
        return task;
    }

    @Override
    public Optional<ScheduledTask> findTaskByKey(ScheduledTaskKey taskKey) {
        return Optional.ofNullable(tasks.get(taskKey));
    }

    @Override
    public PageResponse<ScheduledTask> findTasks(PageRequest pageRequest) {
        List<ScheduledTask> items = tasks.values().stream()
                .sorted(Comparator.comparing(task -> task.taskKey().value()))
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), tasks.size());
    }

    @Override
    public List<ScheduledTask> findDueTasks(Instant now, int limit) {
        return tasks.values().stream()
                .filter(ScheduledTask::enabled)
                .filter(task -> task.registrationStatus() == ScheduledTaskRegistrationStatus.REGISTERED)
                .filter(task -> task.nextFireAt() != null && !task.nextFireAt().isAfter(now))
                .sorted(Comparator.comparing(ScheduledTask::nextFireAt))
                .limit(limit)
                .toList();
    }

    @Override
    public ScheduledTaskPlan savePlan(ScheduledTaskPlan plan) {
        plans.put(plan.planId(), plan);
        return plan;
    }

    @Override
    public Optional<ScheduledTaskPlan> findPlanById(ScheduledTaskPlanId planId) {
        return Optional.ofNullable(plans.get(planId));
    }

    @Override
    public ScheduledTaskRun saveRun(ScheduledTaskRun run) {
        runs.put(run.taskRunId(), run);
        return run;
    }

    @Override
    public Optional<ScheduledTaskRun> findRunById(ScheduledTaskRunId taskRunId) {
        return Optional.ofNullable(runs.get(taskRunId));
    }

    @Override
    public Optional<ScheduledTaskRun> findActiveRunByTaskKey(ScheduledTaskKey taskKey) {
        return findActiveRunByTaskKeyExcluding(taskKey, null);
    }

    @Override
    public Optional<ScheduledTaskRun> findActiveRunByTaskKeyExcluding(ScheduledTaskKey taskKey, ScheduledTaskRunId taskRunId) {
        return runs.values().stream()
                .filter(run -> run.taskKey().equals(taskKey))
                .filter(run -> run.status().active())
                .filter(run -> taskRunId == null || !run.taskRunId().equals(taskRunId))
                .max(Comparator.comparing(ScheduledTaskRun::updatedAt));
    }

    @Override
    public List<ScheduledTaskRun> findPendingRuns(ScheduledTaskTriggerType triggerType, Instant now, int limit) {
        return runs.values().stream()
                .filter(run -> run.triggerType() == triggerType)
                .filter(run -> run.status() == ScheduledTaskRunStatus.PENDING)
                .filter(run -> !run.scheduledFireAt().isAfter(now))
                .sorted(Comparator.comparing(ScheduledTaskRun::scheduledFireAt))
                .limit(limit)
                .toList();
    }

    @Override
    public PageResponse<ScheduledTaskRun> findRuns(ScheduledTaskRunFilter filter, PageRequest pageRequest) {
        ScheduledTaskRunFilter effectiveFilter = filter == null ? ScheduledTaskRunFilter.empty() : filter;
        List<ScheduledTaskRun> filtered = runs.values().stream()
                .filter(run -> effectiveFilter.taskKey() == null || run.taskKey().equals(effectiveFilter.taskKey()))
                .filter(run -> effectiveFilter.status() == null || run.status() == effectiveFilter.status())
                .filter(run -> effectiveFilter.triggerType() == null || run.triggerType() == effectiveFilter.triggerType())
                .filter(run -> effectiveFilter.requestedByUserId() == null
                        || effectiveFilter.requestedByUserId().equals(run.requestedByUserId()))
                .sorted(Comparator.comparing(ScheduledTaskRun::scheduledFireAt).reversed())
                .toList();
        List<ScheduledTaskRun> items = filtered.stream()
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), filtered.size());
    }

    List<ScheduledTaskRun> allRuns() {
        return List.copyOf(runs.values());
    }
}
