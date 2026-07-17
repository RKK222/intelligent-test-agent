package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Objects;

/**
 * Redis 持续中断收敛所需的安全控制面快照；只允许携带 ID、可信路径和来源元数据，不接受 prompt 或回答原文。
 */
public record RunRuntimeLossRequest(
        RunId runId,
        SessionId sessionId,
        UserId userId,
        String agentId,
        String dispatchMessageId,
        String remoteSessionId,
        String workspaceRoot,
        ConversationSourceType sourceType,
        String sourceRefId,
        String traceId) {

    /** 校验收敛、远端取消和关系型终态投影必需的安全字段。 */
    public RunRuntimeLossRequest {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        agentId = DomainValidation.requireText(agentId, "agentId").trim();
        dispatchMessageId = DomainValidation.requireText(dispatchMessageId, "dispatchMessageId").trim();
        // 锚点已落库但远端 Session 尚未创建时 Redis 也可能中断；此时仍需安全终态收敛，但无需远端取消。
        remoteSessionId = remoteSessionId == null || remoteSessionId.isBlank()
                ? null
                : remoteSessionId.trim();
        workspaceRoot = DomainValidation.requireText(workspaceRoot, "workspaceRoot");
        sourceType = sourceType == null ? ConversationSourceType.MANUAL : sourceType;
        sourceRefId = sourceRefId == null || sourceRefId.isBlank() ? null : sourceRefId.trim();
        traceId = DomainValidation.requireText(traceId, "traceId").trim();
    }
}
