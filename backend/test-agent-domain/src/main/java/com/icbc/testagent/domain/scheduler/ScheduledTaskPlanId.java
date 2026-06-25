package com.icbc.testagent.domain.scheduler;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * 用户级 Cron 计划业务 ID，首版仅预留给后续定时会话能力。
 */
public record ScheduledTaskPlanId(String value) {

    /**
     * 校验计划 ID 使用稳定前缀。
     */
    public ScheduledTaskPlanId {
        value = DomainValidation.requirePrefixedId(value, "stp_", "taskPlanId");
    }
}
