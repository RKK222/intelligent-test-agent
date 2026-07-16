package com.enterprise.testagent.opencode.runtime.run.summary;

import com.enterprise.testagent.domain.session.SessionMessageRole;
import java.util.List;
import java.util.Objects;

/**
 * 一次 Run 的 USER/ASSISTANT 双摘要。顺序固定，便于终态事务批量写入最多两条消息。
 */
public record RunConversationSummary(RunMessageSummary user, RunMessageSummary assistant) {

    /**
     * 保证双摘要角色不会颠倒，避免历史页把用户请求与助手结论反向展示。
     */
    public RunConversationSummary {
        user = Objects.requireNonNull(user, "user must not be null");
        assistant = Objects.requireNonNull(assistant, "assistant must not be null");
        if (user.role() != SessionMessageRole.USER) {
            throw new IllegalArgumentException("user summary role must be USER");
        }
        if (assistant.role() != SessionMessageRole.ASSISTANT) {
            throw new IllegalArgumentException("assistant summary role must be ASSISTANT");
        }
    }

    /**
     * 返回稳定的 USER -> ASSISTANT 写入顺序。
     */
    public List<RunMessageSummary> messages() {
        return List.of(user, assistant);
    }
}
