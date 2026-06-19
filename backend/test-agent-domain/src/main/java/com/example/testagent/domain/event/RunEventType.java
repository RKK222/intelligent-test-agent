package com.example.testagent.domain.event;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 平台 RunEvent 类型草案，后续 opencode raw event 必须映射为这些平台事件或新增兼容类型。
 */
public enum RunEventType {
    RUN_CREATED("run.created"),
    RUN_STARTED("run.started"),
    RUN_CANCELLING("run.cancelling"),
    RUN_SUCCEEDED("run.succeeded"),
    RUN_FAILED("run.failed"),
    RUN_CANCELLED("run.cancelled"),
    ASSISTANT_MESSAGE_DELTA("assistant.message.delta"),
    TOOL_STARTED("tool.started"),
    TOOL_FINISHED("tool.finished"),
    DIFF_PROPOSED("diff.proposed"),
    DIFF_ACCEPTED("diff.accepted"),
    DIFF_REJECTED("diff.rejected"),
    TEST_FINISHED("test.finished"),
    OPENCODE_EVENT_UNKNOWN("opencode.event.unknown");

    private static final Map<String, RunEventType> BY_WIRE_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(RunEventType::wireName, type -> type));

    private final String wireName;

    RunEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<RunEventType> fromWireName(String wireName) {
        if (wireName == null || wireName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_WIRE_NAME.get(wireName));
    }
}
