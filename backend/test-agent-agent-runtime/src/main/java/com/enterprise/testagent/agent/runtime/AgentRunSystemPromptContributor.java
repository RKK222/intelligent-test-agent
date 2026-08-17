package com.enterprise.testagent.agent.runtime;

import java.util.Optional;

/** 平台扩展可在根 Run 派发前贡献一段受控 system 上下文；返回空表示无附加上下文。 */
public interface AgentRunSystemPromptContributor {
    Optional<String> contribute(AgentRunPromptContext context);
}
