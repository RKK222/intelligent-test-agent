package com.example.testagent.domain.event;

import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 待追加的运行事件草稿。正式 eventId 和 seq 由持久化层按 append-only 规则分配。
 */
public record RunEventDraft(
        RunId runId,
        RunEventType type,
        String traceId,
        Instant occurredAt,
        Map<String, Object> payload) {

    public RunEventDraft {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        traceId = DomainValidation.requireText(traceId, "traceId");
        occurredAt = DomainValidation.requireInstant(occurredAt, "occurredAt");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
