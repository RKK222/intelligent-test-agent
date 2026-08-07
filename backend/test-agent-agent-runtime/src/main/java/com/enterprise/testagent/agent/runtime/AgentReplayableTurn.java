package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.List;

/** 精确重放所需的用户输入；应用层只能把它保存到带 TTL 的运行态存储。 */
public record AgentReplayableTurn(
        String messageId,
        String prompt,
        List<AgentPromptPart> parts,
        String agent,
        String modelProviderId,
        String modelId,
        String variant) {

    public AgentReplayableTurn {
        messageId = DomainValidation.requireText(messageId, "messageId");
        prompt = DomainValidation.requireText(prompt, "prompt");
        parts = parts == null ? List.of() : List.copyOf(parts);
        agent = optional(agent);
        modelProviderId = optional(modelProviderId);
        modelId = optional(modelId);
        variant = optional(variant);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
