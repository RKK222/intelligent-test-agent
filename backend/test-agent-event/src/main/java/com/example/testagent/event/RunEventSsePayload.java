package com.example.testagent.event;

import com.example.testagent.domain.event.RunEvent;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * SSE body 的稳定平台事件载荷，不暴露 opencode raw event 或 generated DTO。
 */
public record RunEventSsePayload(
        String eventId,
        String runId,
        long seq,
        String type,
        String traceId,
        Instant occurredAt,
        Map<String, Object> payload) {

    public RunEventSsePayload {
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        runId = Objects.requireNonNull(runId, "runId must not be null");
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be greater than 0");
        }
        type = Objects.requireNonNull(type, "type must not be null");
        traceId = Objects.requireNonNull(traceId, "traceId must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static RunEventSsePayload from(RunEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return new RunEventSsePayload(
                event.eventId().value(),
                event.runId().value(),
                event.seq(),
                event.type().wireName(),
                event.traceId(),
                event.occurredAt(),
                event.payload());
    }
}
