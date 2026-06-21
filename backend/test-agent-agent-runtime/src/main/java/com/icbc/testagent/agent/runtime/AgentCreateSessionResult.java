package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * 远端 agent 会话创建结果，只暴露远端会话 ID。
 */
public record AgentCreateSessionResult(String remoteSessionId) {

    /**
     * 远端会话 ID 不能为空。
     */
    public AgentCreateSessionResult {
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
    }
}
