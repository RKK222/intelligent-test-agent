package com.enterprise.testagent.xxljob;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 统一 handler 返回给 XXL 日志的非敏感结构化结果。 */
public record XxlJobTaskExecutionOutcome(
        XxlJobTaskExecutionStatus status,
        String taskKey,
        String taskName,
        String taskRunId,
        String traceId,
        XxlJobConcurrencyPolicy concurrencyPolicy,
        Instant startedAt,
        Instant finishedAt,
        Map<String, Object> result) {

    public XxlJobTaskExecutionOutcome {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        taskKey = requireText(taskKey, "taskKey");
        taskName = requireText(taskName, "taskName");
        taskRunId = requireText(taskRunId, "taskRunId");
        traceId = requireText(traceId, "traceId");
        if (concurrencyPolicy == null) {
            throw new IllegalArgumentException("concurrencyPolicy must not be null");
        }
        if (startedAt == null || finishedAt == null) {
            throw new IllegalArgumentException("execution time must not be null");
        }
        result = result == null || result.isEmpty()
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(result));
    }

    /** 锁被其它节点持有时仍返回完整运行标识，让管理员明确看到本轮没有进入业务处理。 */
    public static XxlJobTaskExecutionOutcome skippedLockHeld(
            String taskKey,
            String taskName,
            String taskRunId,
            String traceId,
            XxlJobConcurrencyPolicy concurrencyPolicy,
            Instant startedAt,
            Instant finishedAt) {
        return new XxlJobTaskExecutionOutcome(
                XxlJobTaskExecutionStatus.SKIPPED_LOCK_HELD,
                taskKey,
                taskName,
                taskRunId,
                traceId,
                concurrencyPolicy,
                startedAt,
                finishedAt,
                Map.of("reason", "GLOBAL_MUTEX_LOCK_HELD"));
    }

    /** 返回业务 handler 是否真正执行，避免把互斥跳过误判为“已处理成功”。 */
    public boolean processed() {
        return status == XxlJobTaskExecutionStatus.SUCCEEDED;
    }

    /** 返回管理员可直接阅读的运行耗时；系统时钟回拨时保守记为零。 */
    public long durationMillis() {
        return Math.max(0L, Duration.between(startedAt, finishedAt).toMillis());
    }

    /** 以稳定字段顺序生成低敏日志对象，业务结果仅来自 ScheduledTaskResult。 */
    public Map<String, Object> toLogFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", status.name());
        fields.put("processed", processed());
        fields.put("taskKey", taskKey);
        fields.put("taskName", taskName);
        fields.put("taskRunId", taskRunId);
        fields.put("traceId", traceId);
        fields.put("concurrencyPolicy", concurrencyPolicy.name());
        fields.put("startedAt", startedAt.toString());
        fields.put("finishedAt", finishedAt.toString());
        fields.put("durationMillis", durationMillis());
        fields.put("result", result);
        return Collections.unmodifiableMap(fields);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
