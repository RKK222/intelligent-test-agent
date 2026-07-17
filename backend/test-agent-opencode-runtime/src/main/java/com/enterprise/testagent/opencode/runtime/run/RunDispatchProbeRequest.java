package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunId;
import java.util.Objects;

/** 恢复接管前的远端 dispatch 查询参数；实现不得将 messageId 或远端响应写入 PostgreSQL。 */
public record RunDispatchProbeRequest(
        RunId runId,
        String agentId,
        String remoteSessionId,
        String dispatchMessageId,
        String executionNodeId,
        String executionNodeBaseUrl,
        String opencodeProcessId,
        String producerLinuxServerId,
        String traceId) {

    public RunDispatchProbeRequest {
        Objects.requireNonNull(runId, "runId must not be null");
        agentId = requireText(agentId, "agentId");
        remoteSessionId = requireText(remoteSessionId, "remoteSessionId");
        dispatchMessageId = requireText(dispatchMessageId, "dispatchMessageId");
        executionNodeBaseUrl = requireText(executionNodeBaseUrl, "executionNodeBaseUrl");
        producerLinuxServerId = requireText(producerLinuxServerId, "producerLinuxServerId");
        traceId = requireText(traceId, "traceId");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
