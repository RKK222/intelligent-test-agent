package com.icbc.testagent.domain.agent;

import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 平台 Session 与某个 agent 远端会话的内部绑定关系。
 * <p>
 * 同一个平台 Session 可以为不同 agent 保存不同远端会话；该对象只服务后端运行编排，
 * 不进入前端 API DTO。
 */
public record AgentSessionBinding(
        SessionId sessionId,
        String agentId,
        String remoteSessionId,
        ExecutionNodeId executionNodeId,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 校验绑定关系不变量，agentId 统一转为小写 URL 标志。
     */
    public AgentSessionBinding {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        agentId = DomainValidation.requireText(agentId, "agentId").trim().toLowerCase(Locale.ROOT);
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        Objects.requireNonNull(executionNodeId, "executionNodeId must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /**
     * 判断是否已有可用远端会话 ID。
     */
    public boolean hasRemoteSession() {
        return remoteSessionId != null && !remoteSessionId.isBlank();
    }

    /**
     * 更新远端会话和节点映射，保留首次创建时间。
     */
    public AgentSessionBinding updateRemoteSession(
            String remoteSessionId,
            ExecutionNodeId executionNodeId,
            Instant updatedAt,
            String traceId) {
        return new AgentSessionBinding(
                sessionId,
                agentId,
                remoteSessionId,
                executionNodeId,
                createdAt,
                updatedAt,
                traceId);
    }
}
