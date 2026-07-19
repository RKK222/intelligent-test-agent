package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 夜间任务 HTTP 响应只能暴露安全预览，不能回传完整 Run 输入。 */
class NightExecutionDtosTest {

    @Test
    void taskResponseContainsPreviewButNoPayload() {
        Instant now = Instant.parse("2026-07-18T12:00:00Z");
        NightExecutionTask task = new NightExecutionTask(
                new NightExecutionTaskId("net_night_dto_test"), new UserId("usr_night_dto"),
                new SessionId("ses_night_dto_test"), new WorkspaceId("wrk_night_dto_test"),
                "request-night-dto", "夜间任务", "安全预览", "{\"secret\":\"never-return\"}",
                NightExecutionTaskStatus.SCHEDULED, Instant.parse("2026-07-18T13:00:00Z"),
                Instant.parse("2026-07-18T13:15:00Z"), Instant.parse("2026-07-18T23:00:00Z"),
                "linux-night", null, null, 0, false, null, null, null, null, null,
                "trace_night_dto", now, now);

        NightExecutionDtos.TaskResponse response = NightExecutionDtos.TaskResponse.from(task);

        assertThat(response.contentPreview()).isEqualTo("安全预览");
        assertThat(response.toString()).doesNotContain("never-return", "runInputJson");
    }
}
