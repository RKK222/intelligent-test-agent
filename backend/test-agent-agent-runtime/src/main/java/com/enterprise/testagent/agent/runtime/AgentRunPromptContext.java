package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.run.Run;
import java.util.Objects;

/** 启动根 Run 前可供平台扩展读取的最小可信上下文；不得长期保存 prompt。 */
public record AgentRunPromptContext(Run run, String prompt, boolean command, String traceId) {
    public AgentRunPromptContext {
        Objects.requireNonNull(run, "run must not be null");
        prompt = prompt == null ? "" : prompt;
        traceId = traceId == null || traceId.isBlank() ? run.traceId() : traceId.trim();
    }
}
