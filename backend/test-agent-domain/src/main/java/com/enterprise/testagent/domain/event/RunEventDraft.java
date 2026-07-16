package com.enterprise.testagent.domain.event;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.support.DomainValidation;
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
        Map<String, Object> payload,
        RunEventScopeContext scopeContext) {

    /**
     * 兼容旧调用方的构造器；无 scope 的事件仍可按原 payload 语义追加。
     */
    public RunEventDraft(
            RunId runId,
            RunEventType type,
            String traceId,
            Instant occurredAt,
            Map<String, Object> payload) {
        this(runId, type, traceId, occurredAt, payload, null);
    }

    /**
     * 校验事件草稿必填字段，并复制 payload，正式 eventId/seq 留给 Repository 分配。
     */
    public RunEventDraft {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        traceId = DomainValidation.requireText(traceId, "traceId");
        occurredAt = DomainValidation.requireInstant(occurredAt, "occurredAt");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
