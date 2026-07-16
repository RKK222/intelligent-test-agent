package com.enterprise.testagent.domain.broadcast;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Map;

/**
 * 后端实例之间的通用广播 envelope，只承载可公开给同集群后端的安全业务载荷。
 */
public record ServerBroadcastEvent(
        String eventId,
        String type,
        String originInstanceId,
        String originLinuxServerId,
        String traceId,
        Instant occurredAt,
        Map<String, Object> payload) {

    /**
     * 校验广播事件最小字段；payload 会复制为不可变 Map，避免发布后被调用方继续修改。
     */
    public ServerBroadcastEvent {
        eventId = DomainValidation.requireText(eventId, "eventId");
        type = DomainValidation.requireText(type, "type");
        originInstanceId = DomainValidation.requireText(originInstanceId, "originInstanceId");
        originLinuxServerId = DomainValidation.requireText(originLinuxServerId, "originLinuxServerId");
        traceId = DomainValidation.requireText(traceId, "traceId");
        occurredAt = DomainValidation.requireInstant(occurredAt, "occurredAt");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
