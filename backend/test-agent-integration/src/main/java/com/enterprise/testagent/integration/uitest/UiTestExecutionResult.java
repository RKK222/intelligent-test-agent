package com.enterprise.testagent.integration.uitest;

import java.time.Instant;
import java.util.List;

/** 平台内部统一的一次 UI 自动化状态视图。 */
public record UiTestExecutionResult(
        String executionId,
        String requestId,
        String caseName,
        UiTestExecutionStatus status,
        Boolean success,
        String message,
        List<String> errors,
        int stepCount,
        double durationSeconds,
        String reportUrl,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {

    public UiTestExecutionResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        message = message == null ? "" : message;
    }
}
