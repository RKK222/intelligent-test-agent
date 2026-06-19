package com.example.testagent.domain.event;

import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 平台运行事件。每个 runId 内 seq 必须单调递增，SSE 续传按 seq 增量读取。
 */
public record RunEvent(
        RunEventId eventId,
        RunId runId,
        long seq,
        RunEventType type,
        String traceId,
        Instant occurredAt,
        Map<String, Object> payload) {

    public RunEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be greater than 0");
        }
        Objects.requireNonNull(type, "type must not be null");
        traceId = DomainValidation.requireText(traceId, "traceId");
        occurredAt = DomainValidation.requireInstant(occurredAt, "occurredAt");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
