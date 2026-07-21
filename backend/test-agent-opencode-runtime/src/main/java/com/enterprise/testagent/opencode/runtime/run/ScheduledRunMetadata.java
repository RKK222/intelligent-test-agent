package com.enterprise.testagent.opencode.runtime.run;

/** 仅在服务端 Scheduled Run 启动期间传递的生命周期元数据，不进入对外 Run DTO。 */
public record ScheduledRunMetadata(String sourceRefId, String dispatchAttemptId) {

    public ScheduledRunMetadata {
        sourceRefId = required(sourceRefId, "sourceRefId");
        dispatchAttemptId = optional(dispatchAttemptId);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
