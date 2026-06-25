package com.icbc.testagent.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskPlan;
import com.icbc.testagent.domain.scheduler.ScheduledTaskPlanId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRegistrationStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRepository;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRun;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunFilter;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 定时任务框架 JDBC Repository，负责任务定义、用户计划和运行记录的统一持久化。
 */
@Repository
public class JdbcScheduledTaskRepository extends JdbcRepositorySupport implements ScheduledTaskRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    private final RowMapper<ScheduledTask> taskRowMapper = (rs, rowNum) -> new ScheduledTask(
            new ScheduledTaskKey(rs.getString("task_key")),
            rs.getString("name"),
            rs.getString("cron_expression"),
            rs.getBoolean("enabled"),
            Duration.ofSeconds(rs.getLong("lock_ttl_seconds")),
            instant(rs, "next_fire_at"),
            ScheduledTaskRegistrationStatus.valueOf(rs.getString("registration_status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"));

    private final RowMapper<ScheduledTaskPlan> planRowMapper = (rs, rowNum) -> new ScheduledTaskPlan(
            new ScheduledTaskPlanId(rs.getString("plan_id")),
            new ScheduledTaskKey(rs.getString("task_key")),
            new UserId(rs.getString("owner_user_id")),
            rs.getString("cron_expression"),
            readMap(rs.getString("payload_json")),
            rs.getBoolean("enabled"),
            instant(rs, "next_fire_at"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"));

    private final RowMapper<ScheduledTaskRun> runRowMapper = (rs, rowNum) -> new ScheduledTaskRun(
            new ScheduledTaskRunId(rs.getString("task_run_id")),
            new ScheduledTaskKey(rs.getString("task_key")),
            planId(rs.getString("plan_id")),
            ScheduledTaskTriggerType.valueOf(rs.getString("trigger_type")),
            ScheduledTaskRunStatus.valueOf(rs.getString("status")),
            userId(rs.getString("requested_by_user_id")),
            instant(rs, "scheduled_fire_at"),
            instant(rs, "started_at"),
            instant(rs, "ended_at"),
            rs.getString("owner_instance_id"),
            rs.getString("skip_reason"),
            rs.getString("error_code"),
            rs.getString("error_message"),
            readMap(rs.getString("result_json")),
            rs.getString("trace_id"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    /**
     * 注入 JDBC client 和 JSON mapper，payload/result 统一保存为文本 JSON。
     */
    public JdbcScheduledTaskRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ScheduledTask saveTask(ScheduledTask task) {
        if (findTaskByKey(task.taskKey()).isPresent()) {
            jdbcClient.sql("""
                            update scheduled_tasks
                            set name = :name, cron_expression = :cronExpression, enabled = :enabled,
                                lock_ttl_seconds = :lockTtlSeconds, next_fire_at = :nextFireAt,
                                registration_status = :registrationStatus, trace_id = :traceId,
                                created_at = :createdAt, updated_at = :updatedAt
                            where task_key = :taskKey
                            """)
                    .params(taskParams(task))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into scheduled_tasks(
                                task_key, name, cron_expression, enabled, lock_ttl_seconds,
                                next_fire_at, registration_status, trace_id, created_at, updated_at
                            )
                            values (
                                :taskKey, :name, :cronExpression, :enabled, :lockTtlSeconds,
                                :nextFireAt, :registrationStatus, :traceId, :createdAt, :updatedAt
                            )
                            """)
                    .params(taskParams(task))
                    .update();
        }
        return task;
    }

    @Override
    public Optional<ScheduledTask> findTaskByKey(ScheduledTaskKey taskKey) {
        return jdbcClient.sql("""
                        select task_key, name, cron_expression, enabled, lock_ttl_seconds,
                               next_fire_at, registration_status, trace_id, created_at, updated_at
                        from scheduled_tasks
                        where task_key = :taskKey
                        """)
                .param("taskKey", taskKey.value())
                .query(taskRowMapper)
                .optional();
    }

    @Override
    public PageResponse<ScheduledTask> findTasks(PageRequest pageRequest) {
        List<ScheduledTask> items = jdbcClient.sql("""
                        select task_key, name, cron_expression, enabled, lock_ttl_seconds,
                               next_fire_at, registration_status, trace_id, created_at, updated_at
                        from scheduled_tasks
                        order by task_key asc
                        limit :limit offset :offset
                        """)
                .param("limit", pageRequest.size())
                .param("offset", pageRequest.offset())
                .query(taskRowMapper)
                .list();
        long total = jdbcClient.sql("select count(*) from scheduled_tasks")
                .query(Long.class)
                .single();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }

    @Override
    public List<ScheduledTask> findDueTasks(Instant now, int limit) {
        return jdbcClient.sql("""
                        select task_key, name, cron_expression, enabled, lock_ttl_seconds,
                               next_fire_at, registration_status, trace_id, created_at, updated_at
                        from scheduled_tasks
                        where enabled = true
                          and registration_status = :registrationStatus
                          and next_fire_at is not null
                          and next_fire_at <= :now
                        order by next_fire_at asc, task_key asc
                        limit :limit
                        """)
                .param("registrationStatus", ScheduledTaskRegistrationStatus.REGISTERED.name())
                .param("now", timestamp(now))
                .param("limit", limit)
                .query(taskRowMapper)
                .list();
    }

    @Override
    public ScheduledTaskPlan savePlan(ScheduledTaskPlan plan) {
        if (findPlanById(plan.planId()).isPresent()) {
            jdbcClient.sql("""
                            update scheduled_task_plans
                            set task_key = :taskKey, owner_user_id = :ownerUserId,
                                cron_expression = :cronExpression, payload_json = :payloadJson,
                                enabled = :enabled, next_fire_at = :nextFireAt,
                                trace_id = :traceId, created_at = :createdAt, updated_at = :updatedAt
                            where plan_id = :planId
                            """)
                    .params(planParams(plan))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into scheduled_task_plans(
                                plan_id, task_key, owner_user_id, cron_expression, payload_json,
                                enabled, next_fire_at, trace_id, created_at, updated_at
                            )
                            values (
                                :planId, :taskKey, :ownerUserId, :cronExpression, :payloadJson,
                                :enabled, :nextFireAt, :traceId, :createdAt, :updatedAt
                            )
                            """)
                    .params(planParams(plan))
                    .update();
        }
        return plan;
    }

    @Override
    public Optional<ScheduledTaskPlan> findPlanById(ScheduledTaskPlanId planId) {
        return jdbcClient.sql("""
                        select plan_id, task_key, owner_user_id, cron_expression, payload_json,
                               enabled, next_fire_at, trace_id, created_at, updated_at
                        from scheduled_task_plans
                        where plan_id = :planId
                        """)
                .param("planId", planId.value())
                .query(planRowMapper)
                .optional();
    }

    @Override
    public ScheduledTaskRun saveRun(ScheduledTaskRun run) {
        if (findRunById(run.taskRunId()).isPresent()) {
            jdbcClient.sql("""
                            update scheduled_task_runs
                            set task_key = :taskKey, plan_id = :planId,
                                trigger_type = :triggerType, status = :status,
                                requested_by_user_id = :requestedByUserId,
                                scheduled_fire_at = :scheduledFireAt, started_at = :startedAt,
                                ended_at = :endedAt, owner_instance_id = :ownerInstanceId,
                                skip_reason = :skipReason, error_code = :errorCode,
                                error_message = :errorMessage, result_json = :resultJson,
                                trace_id = :traceId, created_at = :createdAt, updated_at = :updatedAt
                            where task_run_id = :taskRunId
                            """)
                    .params(runParams(run))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into scheduled_task_runs(
                                task_run_id, task_key, plan_id, trigger_type, status,
                                requested_by_user_id, scheduled_fire_at, started_at, ended_at,
                                owner_instance_id, skip_reason, error_code, error_message,
                                result_json, trace_id, created_at, updated_at
                            )
                            values (
                                :taskRunId, :taskKey, :planId, :triggerType, :status,
                                :requestedByUserId, :scheduledFireAt, :startedAt, :endedAt,
                                :ownerInstanceId, :skipReason, :errorCode, :errorMessage,
                                :resultJson, :traceId, :createdAt, :updatedAt
                            )
                            """)
                    .params(runParams(run))
                    .update();
        }
        return run;
    }

    @Override
    public Optional<ScheduledTaskRun> findRunById(ScheduledTaskRunId taskRunId) {
        return jdbcClient.sql("""
                        select task_run_id, task_key, plan_id, trigger_type, status,
                               requested_by_user_id, scheduled_fire_at, started_at, ended_at,
                               owner_instance_id, skip_reason, error_code, error_message,
                               result_json, trace_id, created_at, updated_at
                        from scheduled_task_runs
                        where task_run_id = :taskRunId
                        """)
                .param("taskRunId", taskRunId.value())
                .query(runRowMapper)
                .optional();
    }

    @Override
    public Optional<ScheduledTaskRun> findActiveRunByTaskKey(ScheduledTaskKey taskKey) {
        return findActiveRunByTaskKey(taskKey, null);
    }

    @Override
    public Optional<ScheduledTaskRun> findActiveRunByTaskKeyExcluding(ScheduledTaskKey taskKey, ScheduledTaskRunId taskRunId) {
        return findActiveRunByTaskKey(taskKey, taskRunId);
    }

    @Override
    public List<ScheduledTaskRun> findPendingRuns(ScheduledTaskTriggerType triggerType, Instant now, int limit) {
        return jdbcClient.sql("""
                        select task_run_id, task_key, plan_id, trigger_type, status,
                               requested_by_user_id, scheduled_fire_at, started_at, ended_at,
                               owner_instance_id, skip_reason, error_code, error_message,
                               result_json, trace_id, created_at, updated_at
                        from scheduled_task_runs
                        where trigger_type = :triggerType
                          and status = :status
                          and scheduled_fire_at <= :now
                        order by scheduled_fire_at asc, id asc
                        limit :limit
                        """)
                .param("triggerType", triggerType.name())
                .param("status", ScheduledTaskRunStatus.PENDING.name())
                .param("now", timestamp(now))
                .param("limit", limit)
                .query(runRowMapper)
                .list();
    }

    @Override
    public PageResponse<ScheduledTaskRun> findRuns(ScheduledTaskRunFilter filter, PageRequest pageRequest) {
        String whereClause = runWhereClause(filter);
        Map<String, Object> params = runFilterParams(filter);
        params.put("limit", pageRequest.size());
        params.put("offset", pageRequest.offset());
        List<ScheduledTaskRun> items = jdbcClient.sql("""
                        select task_run_id, task_key, plan_id, trigger_type, status,
                               requested_by_user_id, scheduled_fire_at, started_at, ended_at,
                               owner_instance_id, skip_reason, error_code, error_message,
                               result_json, trace_id, created_at, updated_at
                        from scheduled_task_runs
                        %s
                        order by scheduled_fire_at desc, id desc
                        limit :limit offset :offset
                        """.formatted(whereClause))
                .params(params)
                .query(runRowMapper)
                .list();
        long total = jdbcClient.sql("""
                        select count(*)
                        from scheduled_task_runs
                        %s
                        """.formatted(whereClause))
                .params(runFilterParams(filter))
                .query(Long.class)
                .single();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }

    private Optional<ScheduledTaskRun> findActiveRunByTaskKey(ScheduledTaskKey taskKey, ScheduledTaskRunId excludedTaskRunId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("taskKey", taskKey.value());
        params.put("excludedTaskRunId", excludedTaskRunId == null ? null : excludedTaskRunId.value());
        return jdbcClient.sql("""
                        select task_run_id, task_key, plan_id, trigger_type, status,
                               requested_by_user_id, scheduled_fire_at, started_at, ended_at,
                               owner_instance_id, skip_reason, error_code, error_message,
                               result_json, trace_id, created_at, updated_at
                        from scheduled_task_runs
                        where task_key = :taskKey
                          and status in ('PENDING', 'RUNNING')
                          and (:excludedTaskRunId is null or task_run_id <> :excludedTaskRunId)
                        order by updated_at desc, id desc
                        limit 1
                        """)
                .params(params)
                .query(runRowMapper)
                .optional();
    }

    private Map<String, Object> taskParams(ScheduledTask task) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("taskKey", task.taskKey().value());
        params.put("name", task.name());
        params.put("cronExpression", task.cronExpression());
        params.put("enabled", task.enabled());
        params.put("lockTtlSeconds", task.lockTtl().toSeconds());
        params.put("nextFireAt", timestamp(task.nextFireAt()));
        params.put("registrationStatus", task.registrationStatus().name());
        params.put("traceId", task.traceId());
        params.put("createdAt", timestamp(task.createdAt()));
        params.put("updatedAt", timestamp(task.updatedAt()));
        return params;
    }

    private Map<String, Object> planParams(ScheduledTaskPlan plan) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("planId", plan.planId().value());
        params.put("taskKey", plan.taskKey().value());
        params.put("ownerUserId", plan.ownerUserId().value());
        params.put("cronExpression", plan.cronExpression());
        params.put("payloadJson", writeMap(plan.payload()));
        params.put("enabled", plan.enabled());
        params.put("nextFireAt", timestamp(plan.nextFireAt()));
        params.put("traceId", plan.traceId());
        params.put("createdAt", timestamp(plan.createdAt()));
        params.put("updatedAt", timestamp(plan.updatedAt()));
        return params;
    }

    private Map<String, Object> runParams(ScheduledTaskRun run) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("taskRunId", run.taskRunId().value());
        params.put("taskKey", run.taskKey().value());
        params.put("planId", run.planId() == null ? null : run.planId().value());
        params.put("triggerType", run.triggerType().name());
        params.put("status", run.status().name());
        params.put("requestedByUserId", run.requestedByUserId() == null ? null : run.requestedByUserId().value());
        params.put("scheduledFireAt", timestamp(run.scheduledFireAt()));
        params.put("startedAt", timestamp(run.startedAt()));
        params.put("endedAt", timestamp(run.endedAt()));
        params.put("ownerInstanceId", run.ownerInstanceId());
        params.put("skipReason", run.skipReason());
        params.put("errorCode", run.errorCode());
        params.put("errorMessage", run.errorMessage());
        params.put("resultJson", writeMap(run.result()));
        params.put("traceId", run.traceId());
        params.put("createdAt", timestamp(run.createdAt()));
        params.put("updatedAt", timestamp(run.updatedAt()));
        return params;
    }

    private String runWhereClause(ScheduledTaskRunFilter filter) {
        if (filter == null) {
            return "";
        }
        List<String> predicates = new java.util.ArrayList<>();
        if (filter.taskKey() != null) {
            predicates.add("task_key = :taskKey");
        }
        if (filter.status() != null) {
            predicates.add("status = :status");
        }
        if (filter.triggerType() != null) {
            predicates.add("trigger_type = :triggerType");
        }
        if (filter.requestedByUserId() != null) {
            predicates.add("requested_by_user_id = :requestedByUserId");
        }
        return predicates.isEmpty() ? "" : "where " + String.join(" and ", predicates);
    }

    private Map<String, Object> runFilterParams(ScheduledTaskRunFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (filter == null) {
            return params;
        }
        if (filter.taskKey() != null) {
            params.put("taskKey", filter.taskKey().value());
        }
        if (filter.status() != null) {
            params.put("status", filter.status().name());
        }
        if (filter.triggerType() != null) {
            params.put("triggerType", filter.triggerType().name());
        }
        if (filter.requestedByUserId() != null) {
            params.put("requestedByUserId", filter.requestedByUserId().value());
        }
        return params;
    }

    private ScheduledTaskPlanId planId(String value) {
        return value == null ? null : new ScheduledTaskPlanId(value);
    }

    private UserId userId(String value) {
        return value == null ? null : new UserId(value);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
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
