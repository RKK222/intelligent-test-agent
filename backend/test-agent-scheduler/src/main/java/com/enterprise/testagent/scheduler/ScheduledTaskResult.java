package com.enterprise.testagent.scheduler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * handler 返回给框架的结构化结果，由框架统一写入 scheduled_task_runs.result_json。
 */
public record ScheduledTaskResult(Map<String, Object> result) {

    /**
     * 固化结果 Map，避免运行记录保存前被调用方继续修改。
     */
    public ScheduledTaskResult {
        if (result == null || result.isEmpty()) {
            result = Map.of();
        } else {
            result = Map.copyOf(new LinkedHashMap<>(result));
        }
    }

    /**
     * 返回空结果，适合没有业务输出的任务。
     */
    public static ScheduledTaskResult empty() {
        return new ScheduledTaskResult(Map.of());
    }

    /**
     * 使用结构化 Map 构造结果。
     */
    public static ScheduledTaskResult of(Map<String, Object> result) {
        return new ScheduledTaskResult(result);
    }
}
