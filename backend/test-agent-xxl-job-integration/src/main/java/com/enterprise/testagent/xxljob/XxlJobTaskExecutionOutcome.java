package com.enterprise.testagent.xxljob;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一 handler 返回给 XXL 日志的非敏感结构化结果。 */
public record XxlJobTaskExecutionOutcome(XxlJobTaskExecutionStatus status, Map<String, Object> result) {

    public XxlJobTaskExecutionOutcome {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        result = result == null || result.isEmpty()
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(result));
    }

    public static XxlJobTaskExecutionOutcome skippedLockHeld() {
        return new XxlJobTaskExecutionOutcome(
                XxlJobTaskExecutionStatus.SKIPPED_LOCK_HELD,
                Map.of("status", "SKIPPED_LOCK_HELD"));
    }
}
