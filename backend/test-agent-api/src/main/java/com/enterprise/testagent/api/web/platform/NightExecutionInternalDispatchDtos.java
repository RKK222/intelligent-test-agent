package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchBatchResult;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchResult;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 内部夜间分发 DTO；跨服务器只传目标服务器和任务 ID，不传 prompt、附件或用户信息。 */
final class NightExecutionInternalDispatchDtos {

    private NightExecutionInternalDispatchDtos() { }

    record Request(
            @NotBlank @Size(max = 128) String linuxServerId,
            @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 128) String> taskIds) {

        List<NightExecutionTaskId> domainTaskIds() {
            return taskIds.stream().map(NightExecutionTaskId::new).toList();
        }
    }

    record Response(String linuxServerId, List<TaskResult> results) {
        static Response from(NightExecutionDispatchBatchResult batch) {
            return new Response(
                    batch.linuxServerId(),
                    batch.results().stream().map(TaskResult::from).toList());
        }

        NightExecutionDispatchBatchResult toDomain() {
            return new NightExecutionDispatchBatchResult(
                    linuxServerId,
                    results.stream().map(TaskResult::toDomain).toList());
        }
    }

    record TaskResult(String taskId, String status, String runId, String errorCode) {
        static TaskResult from(NightExecutionDispatchResult result) {
            return new TaskResult(
                    result.taskId().value(), result.status().name(), result.runId(), result.errorCode());
        }

        NightExecutionDispatchResult toDomain() {
            return new NightExecutionDispatchResult(
                    new NightExecutionTaskId(taskId),
                    NightExecutionDispatchStatus.valueOf(status),
                    runId,
                    errorCode);
        }
    }
}
