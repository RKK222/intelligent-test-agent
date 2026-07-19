package com.enterprise.testagent.domain.nightexecution;

import java.util.Objects;

/** 夜间异步执行任务稳定标识。 */
public record NightExecutionTaskId(String value) {
    public NightExecutionTaskId {
        value = Objects.requireNonNull(value, "taskId must not be null").trim();
        if (!value.matches("net_[a-zA-Z0-9_-]{8,60}")) throw new IllegalArgumentException("invalid night task id");
    }
}
