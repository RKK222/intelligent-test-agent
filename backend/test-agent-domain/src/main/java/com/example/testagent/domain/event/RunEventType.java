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
    MESSAGE_UPDATED("message.updated"),
    MESSAGE_REMOVED("message.removed"),
    MESSAGE_PART_UPDATED("message.part.updated"),
    MESSAGE_PART_REMOVED("message.part.removed"),
    MESSAGE_PART_DELTA("message.part.delta"),
    SESSION_DIFF("session.diff"),
    SESSION_STATUS("session.status"),
    TODO_UPDATED("todo.updated"),
    TOOL_STARTED("tool.started"),
    TOOL_FINISHED("tool.finished"),
    DIFF_PROPOSED("diff.proposed"),
    DIFF_ACCEPTED("diff.accepted"),
    DIFF_REJECTED("diff.rejected"),
    TEST_FINISHED("test.finished"),
    PERMISSION_ASKED("permission.asked"),
    PERMISSION_REPLIED("permission.replied"),
    QUESTION_ASKED("question.asked"),
    QUESTION_REPLIED("question.replied"),
    QUESTION_REJECTED("question.rejected"),
    VCS_BRANCH_UPDATED("vcs.branch.updated"),
    LSP_UPDATED("lsp.updated"),
    MCP_TOOLS_CHANGED("mcp.tools.changed"),
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
