package com.icbc.testagent.domain.event;

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
    RUN_SNAPSHOT_RESET("run.snapshot.reset"),
    SIDE_QUESTION_STARTED("side_question.started"),
    SIDE_QUESTION_PROGRESS("side_question.progress"),
    SIDE_QUESTION_DELTA("side_question.delta"),
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
    SESSION_CREATED("session.created"),
    SESSION_UPDATED("session.updated"),
    SESSION_DELETED("session.deleted"),
    TEST_FINISHED("test.finished"),
    SESSION_ERROR("session.error"),
    SESSION_CHILD_DISCOVERED("session.child.discovered"),
    SESSION_SCOPE_UPDATED("session.scope.updated"),
    PERMISSION_ASKED("permission.asked"),
    PERMISSION_REPLIED("permission.replied"),
    QUESTION_ASKED("question.asked"),
    QUESTION_REPLIED("question.replied"),
    QUESTION_REJECTED("question.rejected"),
    VCS_BRANCH_UPDATED("vcs.branch.updated"),
    LSP_UPDATED("lsp.updated"),
    MCP_TOOLS_CHANGED("mcp.tools.changed"),
    REFERENCE_UPDATED("reference.updated"),
    FILE_EDITED("file.edited"),
    FILE_WATCHER_UPDATED("file.watcher.updated"),
    OPENCODE_EVENT_UNKNOWN("opencode.event.unknown");

    private static final Map<String, RunEventType> BY_WIRE_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(RunEventType::wireName, type -> type));

    private final String wireName;

    /**
     * 绑定平台事件枚举到稳定 wireName，wireName 用于数据库和 SSE payload。
     */
    RunEventType(String wireName) {
        this.wireName = wireName;
    }

    /**
     * 返回对外稳定事件类型名称。
     */
    public String wireName() {
        return wireName;
    }

    /**
     * 根据 wireName 查找事件类型，未知或空值返回 Optional.empty 供调用方决定兼容策略。
     */
    public static Optional<RunEventType> fromWireName(String wireName) {
        if (wireName == null || wireName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_WIRE_NAME.get(wireName));
    }
}
