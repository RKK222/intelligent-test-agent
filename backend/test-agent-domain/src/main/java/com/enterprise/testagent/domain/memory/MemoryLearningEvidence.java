package com.enterprise.testagent.domain.memory;

import java.time.Instant;
import java.util.List;

/** 从既有 Session/Run 恢复链瞬时读取的本轮消息；禁止写入记忆治理表或 Mem0。 */
public record MemoryLearningEvidence(String runId, List<Message> messages) {
    public MemoryLearningEvidence {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    @Override
    public String toString() {
        return "MemoryLearningEvidence[runId=" + runId + ", messageCount=" + messages.size() + "]";
    }

    public record Message(String role, String content, Instant createdAt) {
        @Override
        public String toString() {
            return "Message[role=" + role + ", contentLength="
                    + (content == null ? 0 : content.length()) + ", createdAt=" + createdAt + "]";
        }
    }
}
