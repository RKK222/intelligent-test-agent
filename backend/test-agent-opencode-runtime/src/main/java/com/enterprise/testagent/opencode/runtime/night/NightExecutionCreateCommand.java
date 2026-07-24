package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.nightexecution.NightExecutionScheduleMode;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/** 创建夜间任务命令；owner、状态、容量和来源均由服务端决定。 */
public record NightExecutionCreateCommand(
        String clientRequestId,
        SessionId sessionId,
        WorkspaceId workspaceId,
        String sessionTitle,
        NightExecutionRunInputSnapshot runInput,
        NightExecutionScheduleMode scheduleMode,
        Instant slotStart) {

    public NightExecutionCreateCommand {
        clientRequestId = required(clientRequestId, "clientRequestId");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        sessionTitle = sessionTitle == null || sessionTitle.isBlank() ? null : sessionTitle.trim();
        Objects.requireNonNull(runInput, "runInput must not be null");
        scheduleMode = scheduleMode == null ? NightExecutionScheduleMode.NIGHT_WINDOW : scheduleMode;
        Objects.requireNonNull(slotStart, "slotStart must not be null");
    }

    /** 兼容未携带模式的既有调用方，保持标准夜间窗口语义。 */
    public NightExecutionCreateCommand(
            String clientRequestId,
            SessionId sessionId,
            WorkspaceId workspaceId,
            String sessionTitle,
            NightExecutionRunInputSnapshot runInput,
            Instant slotStart) {
        this(clientRequestId, sessionId, workspaceId, sessionTitle, runInput,
                NightExecutionScheduleMode.NIGHT_WINDOW, slotStart);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
