package com.enterprise.testagent.persistence.mybatis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
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
import com.enterprise.testagent.domain.user.UserId;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** scheduler 领域仓储的 MyBatis 适配器。 */
@Repository
public class MyBatisScheduledTaskRepository implements ScheduledTaskRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ScheduledTaskMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisScheduledTaskRepository(ScheduledTaskMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ScheduledTask saveTask(ScheduledTask task) {
        Map<String, Object> params = taskParams(task);
        if (mapper.updateTask(params) == 0) {
            mapper.insertTask(params);
        }
        return task;
    }

    @Override
    public Optional<ScheduledTask> findTaskByKey(ScheduledTaskKey taskKey) {
        return Optional.ofNullable(mapper.findTaskByKey(taskKey.value())).map(this::task);
    }

    @Override
    public PageResponse<ScheduledTask> findTasks(PageRequest pageRequest) {
        return new PageResponse<>(
                mapper.findTasks(pageRequest.size(), pageRequest.offset()).stream().map(this::task).toList(),
                pageRequest.page(), pageRequest.size(), mapper.countTasks());
    }

    @Override
    public List<ScheduledTask> findDueTasks(Instant now, int limit) {
        return mapper.findDueTasks(now, limit).stream().map(this::task).toList();
    }

    @Override
    public ScheduledTaskPlan savePlan(ScheduledTaskPlan plan) {
        Map<String, Object> params = planParams(plan);
        if (mapper.updatePlan(params) == 0) {
            mapper.insertPlan(params);
        }
        return plan;
    }

    @Override
    public Optional<ScheduledTaskPlan> findPlanById(ScheduledTaskPlanId planId) {
        return Optional.ofNullable(mapper.findPlanById(planId.value())).map(this::plan);
    }

    @Override
    public ScheduledTaskRun saveRun(ScheduledTaskRun run) {
        Map<String, Object> params = runParams(run);
        if (mapper.updateRun(params) == 0) {
            mapper.insertRun(params);
        }
        return run;
    }

    @Override
    public boolean updateRunIfStatus(ScheduledTaskRun run, ScheduledTaskRunStatus expectedStatus) {
        Map<String, Object> params = runParams(run);
        params.put("expectedStatus", expectedStatus.name());
        return mapper.updateRunIfStatus(params) == 1;
    }

    @Override
    public Optional<ScheduledTaskRun> findRunById(ScheduledTaskRunId taskRunId) {
        return Optional.ofNullable(mapper.findRunById(taskRunId.value())).map(this::run);
    }

    @Override
    public Optional<ScheduledTaskRun> findActiveRunByTaskKey(ScheduledTaskKey taskKey) {
        return findActive(taskKey, null);
    }

    @Override
    public Optional<ScheduledTaskRun> findActiveRunByTaskKeyExcluding(
            ScheduledTaskKey taskKey, ScheduledTaskRunId taskRunId) {
        return findActive(taskKey, taskRunId == null ? null : taskRunId.value());
    }

    private Optional<ScheduledTaskRun> findActive(ScheduledTaskKey taskKey, String excludedTaskRunId) {
        return Optional.ofNullable(mapper.findActiveRunByTaskKey(taskKey.value(), excludedTaskRunId)).map(this::run);
    }

    @Override
    public List<ScheduledTaskRun> findPendingRuns(
            ScheduledTaskTriggerType triggerType, String executionAffinity, Instant now, int limit) {
        return mapper.findPendingRuns(triggerType.name(), executionAffinity, now, limit).stream().map(this::run).toList();
    }

    @Override
    public PageResponse<ScheduledTaskRun> findRuns(ScheduledTaskRunFilter filter, PageRequest pageRequest) {
        ScheduledTaskRunFilter resolved = filter == null ? ScheduledTaskRunFilter.empty() : filter;
        String taskKey = resolved.taskKey() == null ? null : resolved.taskKey().value();
        String status = resolved.status() == null ? null : resolved.status().name();
        String trigger = resolved.triggerType() == null ? null : resolved.triggerType().name();
        String userId = resolved.requestedByUserId() == null ? null : resolved.requestedByUserId().value();
        List<ScheduledTaskRun> items = mapper.findRuns(
                        taskKey, status, trigger, userId, pageRequest.size(), pageRequest.offset())
                .stream().map(this::run).toList();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(),
                mapper.countRuns(taskKey, status, trigger, userId));
    }

    private ScheduledTask task(Map<String, Object> row) {
        return new ScheduledTask(
                new ScheduledTaskKey(text(row, "taskKey")), text(row, "name"), text(row, "cronExpression"),
                bool(row, "enabled"), Duration.ofSeconds(number(row, "lockTtlSeconds").longValue()),
                instant(row, "nextFireAt"), ScheduledTaskRegistrationStatus.valueOf(text(row, "registrationStatus")),
                instant(row, "createdAt"), instant(row, "updatedAt"), text(row, "traceId"));
    }

    private ScheduledTaskPlan plan(Map<String, Object> row) {
        return new ScheduledTaskPlan(
                new ScheduledTaskPlanId(text(row, "planId")), new ScheduledTaskKey(text(row, "taskKey")),
                new UserId(text(row, "ownerUserId")), text(row, "cronExpression"), readMap(text(row, "payloadJson")),
                bool(row, "enabled"), instant(row, "nextFireAt"), instant(row, "createdAt"),
                instant(row, "updatedAt"), text(row, "traceId"));
    }

    private ScheduledTaskRun run(Map<String, Object> row) {
        return new ScheduledTaskRun(
                new ScheduledTaskRunId(text(row, "taskRunId")), new ScheduledTaskKey(text(row, "taskKey")),
                optional(row, "planId").map(ScheduledTaskPlanId::new).orElse(null),
                ScheduledTaskTriggerType.valueOf(text(row, "triggerType")),
                ScheduledTaskRunStatus.valueOf(text(row, "status")),
                optional(row, "requestedByUserId").map(UserId::new).orElse(null),
                instant(row, "scheduledFireAt"), instant(row, "startedAt"), instant(row, "endedAt"),
                text(row, "ownerInstanceId"), instant(row, "stopRequestedAt"),
                optional(row, "stopRequestedByUserId").map(UserId::new).orElse(null),
                text(row, "stopReason"), text(row, "skipReason"), text(row, "errorCode"),
                text(row, "errorMessage"), readMap(text(row, "resultJson")), text(row, "traceId"),
                instant(row, "createdAt"), instant(row, "updatedAt"), text(row, "executionAffinity"));
    }

    private Map<String, Object> taskParams(ScheduledTask task) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("taskKey", task.taskKey().value()); values.put("name", task.name());
        values.put("cronExpression", task.cronExpression()); values.put("enabled", task.enabled());
        values.put("lockTtlSeconds", task.lockTtl().toSeconds()); values.put("nextFireAt", task.nextFireAt());
        values.put("registrationStatus", task.registrationStatus().name()); values.put("traceId", task.traceId());
        values.put("createdAt", task.createdAt()); values.put("updatedAt", task.updatedAt());
        return values;
    }

    private Map<String, Object> planParams(ScheduledTaskPlan plan) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("planId", plan.planId().value()); values.put("taskKey", plan.taskKey().value());
        values.put("ownerUserId", plan.ownerUserId().value()); values.put("cronExpression", plan.cronExpression());
        values.put("payloadJson", writeMap(plan.payload())); values.put("enabled", plan.enabled());
        values.put("nextFireAt", plan.nextFireAt()); values.put("traceId", plan.traceId());
        values.put("createdAt", plan.createdAt()); values.put("updatedAt", plan.updatedAt());
        return values;
    }

    private Map<String, Object> runParams(ScheduledTaskRun run) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("taskRunId", run.taskRunId().value()); values.put("taskKey", run.taskKey().value());
        values.put("planId", run.planId() == null ? null : run.planId().value());
        values.put("triggerType", run.triggerType().name()); values.put("status", run.status().name());
        values.put("requestedByUserId", run.requestedByUserId() == null ? null : run.requestedByUserId().value());
        values.put("scheduledFireAt", run.scheduledFireAt()); values.put("startedAt", run.startedAt());
        values.put("endedAt", run.endedAt()); values.put("ownerInstanceId", run.ownerInstanceId());
        values.put("stopRequestedAt", run.stopRequestedAt());
        values.put("stopRequestedByUserId", run.stopRequestedByUserId() == null ? null : run.stopRequestedByUserId().value());
        values.put("stopReason", run.stopReason()); values.put("skipReason", run.skipReason());
        values.put("errorCode", run.errorCode()); values.put("errorMessage", run.errorMessage());
        values.put("resultJson", writeMap(run.result())); values.put("traceId", run.traceId());
        values.put("createdAt", run.createdAt()); values.put("updatedAt", run.updatedAt());
        values.put("executionAffinity", run.executionAffinity());
        return values;
    }

    private Optional<String> optional(Map<String, Object> row, String key) {
        return Optional.ofNullable(text(row, key)).filter(value -> !value.isBlank());
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : number(row, key).intValue() != 0;
    }

    private Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof LocalDateTime localDateTime) return localDateTime.toInstant(ZoneOffset.UTC);
        throw new PlatformException(ErrorCode.INTERNAL_ERROR, "定时任务时间字段解析失败");
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "定时任务 JSON 字段解析失败", Map.of(), exception);
        }
    }

    private String writeMap(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "定时任务 JSON 字段序列化失败", Map.of(), exception);
        }
    }
}
