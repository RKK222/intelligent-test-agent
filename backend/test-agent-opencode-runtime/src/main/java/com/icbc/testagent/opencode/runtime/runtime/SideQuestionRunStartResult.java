package com.icbc.testagent.opencode.runtime.runtime;

import com.icbc.testagent.domain.run.RunId;
import java.util.Objects;

/**
 * 旁路问答流式启动结果；HTTP 层只需把 Run ID 返回给现有 RunEvent SSE 客户端。
 */
public record SideQuestionRunStartResult(RunId runId) {

    /** 确保启动结果始终携带可订阅的 Run ID。 */
    public SideQuestionRunStartResult {
        Objects.requireNonNull(runId, "runId must not be null");
    }
}
