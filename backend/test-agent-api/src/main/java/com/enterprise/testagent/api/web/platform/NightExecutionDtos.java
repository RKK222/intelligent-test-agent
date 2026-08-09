package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.nightexecution.NightExecutionScheduleMode;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionCreateCommand;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionRunInputSnapshot;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionTaskQueryResult;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionWindowCalculator;
import com.enterprise.testagent.opencode.runtime.run.StartRunInput;
import com.enterprise.testagent.opencode.runtime.session.BatchContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/** 夜间执行 HTTP DTO，完整输入只允许进入创建命令，不出现在任何响应中。 */
final class NightExecutionDtos {

    private NightExecutionDtos() { }

    record CreateTaskRequest(
            @NotBlank @Size(max = 128) String clientRequestId,
            String sessionId,
            @NotBlank String workspaceId,
            @Size(max = 255) String sessionTitle,
            String prompt,
            List<RuntimeDtos.PromptPartRequest> parts,
            @Size(max = 128) String messageId,
            String agent,
            String model,
            String variant,
            String mode,
            String command,
            String arguments,
            @Size(max = 128) String runClientRequestId,
            NightExecutionScheduleMode scheduleMode,
            @NotNull Instant slotStart,
            @Valid RuntimeDtos.BatchContextRequest batchContext) {

        @AssertTrue(message = "prompt or text part must not be blank")
        boolean hasPromptText() {
            if (prompt != null && !prompt.isBlank()) return true;
            return parts != null && parts.stream().anyMatch(part -> part != null
                    && "text".equals(part.type()) && part.text() != null && !part.text().isBlank());
        }

        @AssertTrue(message = "batchContext requires sessionId to be omitted")
        boolean hasValidBatchTarget() {
            return batchContext == null || sessionId == null || sessionId.isBlank();
        }

        NightExecutionCreateCommand toCommand() {
            List<StartRunInput.PromptPart> inputParts = parts == null
                    ? List.of()
                    : parts.stream().filter(part -> part != null).map(RuntimeDtos.PromptPartRequest::toInputPart).toList();
            return new NightExecutionCreateCommand(
                    clientRequestId,
                    sessionId == null || sessionId.isBlank() ? null : new SessionId(sessionId),
                    new WorkspaceId(workspaceId),
                    sessionTitle,
                    new NightExecutionRunInputSnapshot(
                            prompt, inputParts, messageId, agent, model, variant, mode,
                            command, arguments, runClientRequestId),
                    scheduleMode == null ? NightExecutionScheduleMode.NIGHT_WINDOW : scheduleMode,
                    slotStart,
                    batchContext == null
                            ? null
                            : new BatchContext(batchContext.batchId(), batchContext.itemRequestId()));
        }
    }

    record AdjustTaskRequest(@NotNull Instant slotStart) { }

    record SlotResponse(
            Instant slotStart,
            Instant slotEnd,
            int reservedCount,
            int capacity,
            boolean available,
            boolean recommended) {

        static SlotResponse from(NightExecutionWindowCalculator.NightExecutionSlot slot) {
            return new SlotResponse(
                    slot.slotStart(), slot.slotEnd(), slot.reservedCount(), slot.capacity(),
                    slot.available(), slot.recommended());
        }
    }

    record SlotsResponse(
            String timeZone,
            Instant windowStart,
            Instant windowEnd,
            int capacity,
            List<SlotResponse> slots) {

        static SlotsResponse from(NightExecutionWindowCalculator.NightExecutionWindow window) {
            return new SlotsResponse(
                    window.timeZone(), window.windowStart(), window.windowEnd(), window.capacity(),
                    window.slots().stream().map(SlotResponse::from).toList());
        }
    }

    record TaskResponse(
            String taskId,
            String sessionId,
            String workspaceId,
            String sessionTitle,
            String contentPreview,
            String status,
            String scheduleMode,
            Instant slotStart,
            Instant slotEnd,
            Instant windowEnd,
            int rolloverCount,
            String runId,
            String errorCode,
            String errorMessage,
            String creatorUserId,
            String creatorUsername,
            String creatorUnifiedAuthId,
            boolean createdBySharedUser,
            Instant createdAt,
            Instant updatedAt) {

        static TaskResponse from(NightExecutionTask task) {
            return from(task, null);
        }

        static TaskResponse from(
                NightExecutionTask task,
                Function<UserId, String> usernameLookup) {
            if (task == null) return null;
            return new TaskResponse(
                    task.taskId().value(), task.sessionId().value(), task.workspaceId().value(),
                    task.sessionTitle(), task.contentPreview(), task.status().name(),
                    task.scheduleMode().name(), task.slotStart(),
                    task.slotEnd(), task.windowEnd(), task.rolloverCount(),
                    task.runId() == null ? null : task.runId().value(),
                    task.errorCode(), task.errorMessage(), task.creatorUserId().value(),
                    usernameLookup == null ? null : usernameLookup.apply(task.creatorUserId()),
                    task.creatorUnifiedAuthId(), task.createdBySharedUser(),
                    task.createdAt(), task.updatedAt());
        }
    }

    record TaskQueryResponse(
            List<TaskResponse> items,
            int page,
            int size,
            long total,
            TaskResponse visibleFailure) {

        static TaskQueryResponse from(NightExecutionTaskQueryResult result) {
            return from(result, null);
        }

        static TaskQueryResponse from(
                NightExecutionTaskQueryResult result,
                Function<UserId, String> usernameLookup) {
            PageResponse<NightExecutionTask> page = result.pendingTasks();
            return new TaskQueryResponse(
                    page.items().stream().map(task -> TaskResponse.from(task, usernameLookup)).toList(),
                    page.page(), page.size(), page.total(),
                    TaskResponse.from(result.visibleFailure(), usernameLookup));
        }
    }
}
