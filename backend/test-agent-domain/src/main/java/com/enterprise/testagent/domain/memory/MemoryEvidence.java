package com.enterprise.testagent.domain.memory;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/** 原始聊天的安全引用和不超过 200 字的证据摘要；不包含消息正文。 */
public record MemoryEvidence(
        String evidenceId,
        MemoryId memoryId,
        String runId,
        String sessionId,
        String observedUserId,
        MemorySource source,
        String summary,
        Instant observedAt) {

    public MemoryEvidence {
        evidenceId = DomainValidation.requireText(evidenceId, "evidenceId");
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        runId = DomainValidation.requireText(runId, "runId");
        sessionId = DomainValidation.requireText(sessionId, "sessionId");
        observedUserId = DomainValidation.requireText(observedUserId, "observedUserId");
        Objects.requireNonNull(source, "source must not be null");
        summary = DomainValidation.requireText(summary, "summary");
        if (summary.codePointCount(0, summary.length()) > 200) {
            throw new IllegalArgumentException("evidence summary must not exceed 200 characters");
        }
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }
}
