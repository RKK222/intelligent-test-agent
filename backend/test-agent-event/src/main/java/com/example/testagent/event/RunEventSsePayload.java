package com.example.testagent.event;

import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.event.RunEventDraft;
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

    /**
     * 校验 SSE payload 的稳定字段，并复制 payload map，避免上游在发出事件后继续修改内容。
     */
    public RunEventSsePayload {
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        runId = Objects.requireNonNull(runId, "runId must not be null");
        if (seq < 0) {
            throw new IllegalArgumentException("seq must not be negative");
        }
        type = Objects.requireNonNull(type, "type must not be null");
        traceId = Objects.requireNonNull(traceId, "traceId must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /**
     * 从 durable RunEvent 构造 SSE body，保留持久化 eventId、seq、traceId 和业务 payload。
     */
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

    /**
     * 从 transient draft 构造 SSE body，seq 固定为 0，eventId 由 live bus 生成且不可用于续传。
     */
    public static RunEventSsePayload transientFrom(RunEventDraft draft, String eventId) {
        Objects.requireNonNull(draft, "draft must not be null");
        return new RunEventSsePayload(
                eventId,
                draft.runId().value(),
                0L,
                draft.type().wireName(),
                draft.traceId(),
                draft.occurredAt(),
                draft.payload());
    }
}
