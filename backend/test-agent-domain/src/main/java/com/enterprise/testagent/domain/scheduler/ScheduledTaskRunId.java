package com.enterprise.testagent.domain.scheduler;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * 定时任务单次运行记录业务 ID。
 */
public record ScheduledTaskRunId(String value) {

    /**
     * 校验运行记录 ID 使用稳定前缀，避免与其他业务 ID 混用。
     */
    public ScheduledTaskRunId {
        value = DomainValidation.requirePrefixedId(value, "str_", "taskRunId");
    }
}
