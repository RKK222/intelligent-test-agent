package com.icbc.testagent.domain.agent;

import com.icbc.testagent.domain.session.SessionId;
import java.util.Optional;

/**
 * Agent 会话绑定持久化端口，业务层通过该端口读写通用 agent 远端会话映射。
 */
public interface AgentSessionBindingRepository {

    /**
     * 保存或更新绑定关系。
     */
    AgentSessionBinding save(AgentSessionBinding binding);

    /**
     * 按平台 Session 和 agent 标志查询绑定。
     */
    Optional<AgentSessionBinding> findBySessionIdAndAgentId(SessionId sessionId, String agentId);

    /**
     * 按 agent 标志和远端会话 ID 查询绑定，供后续远端事件反查平台 Session 使用。
     */
    Optional<AgentSessionBinding> findByAgentIdAndRemoteSessionId(String agentId, String remoteSessionId);
}
