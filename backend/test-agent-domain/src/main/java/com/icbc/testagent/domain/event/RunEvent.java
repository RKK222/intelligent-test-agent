package com.icbc.testagent.domain.event;

import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.support.DomainValidation;
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
        Map<String, Object> payload,
        RunEventScopeContext scopeContext) {

    /**
     * 兼容旧持久化读取路径；历史事件没有结构化 scope 时仍按 payload 原样展示。
     */
    public RunEvent(
            RunEventId eventId,
            RunId runId,
            long seq,
            RunEventType type,
            String traceId,
            Instant occurredAt,
            Map<String, Object> payload) {
        this(eventId, runId, seq, type, traceId, occurredAt, payload, null);
    }

    /**
     * 校验已持久化事件的不变量，并复制 payload 保持事件不可变。
     */
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
