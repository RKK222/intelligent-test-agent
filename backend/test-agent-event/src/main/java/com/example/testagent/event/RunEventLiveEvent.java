package com.example.testagent.event;

import java.util.Objects;

/**
 * 内存实时通道中的事件包裹对象，用 durable 标记决定 SSE 是否携带可恢复 id。
 */
public record RunEventLiveEvent(boolean durable, RunEventSsePayload payload) {

    public RunEventLiveEvent {
        payload = Objects.requireNonNull(payload, "payload must not be null");
        if (durable && payload.seq() <= 0) {
            throw new IllegalArgumentException("durable live event seq must be greater than 0");
        }
        if (!durable && payload.seq() != 0) {
            throw new IllegalArgumentException("transient live event seq must be 0");
        }
    }

    public static RunEventLiveEvent durable(RunEventSsePayload payload) {
        return new RunEventLiveEvent(true, payload);
    }

    public static RunEventLiveEvent transientOnly(RunEventSsePayload payload) {
        return new RunEventLiveEvent(false, payload);
    }
}
