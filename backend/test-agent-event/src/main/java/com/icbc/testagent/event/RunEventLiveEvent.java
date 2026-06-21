package com.icbc.testagent.event;

import java.util.Objects;

/**
 * 内存实时通道中的事件包裹对象，用 durable 标记决定 SSE 是否携带可恢复 id。
 */
public record RunEventLiveEvent(boolean durable, RunEventSsePayload payload) {

    /**
     * 校验 live event 与 payload seq 的一致性；durable 必须可续传，transient 必须不可续传。
     */
    public RunEventLiveEvent {
        payload = Objects.requireNonNull(payload, "payload must not be null");
        if (durable && payload.seq() <= 0) {
            throw new IllegalArgumentException("durable live event seq must be greater than 0");
        }
        if (!durable && payload.seq() != 0) {
            throw new IllegalArgumentException("transient live event seq must be 0");
        }
    }

    /**
     * 包装已持久化事件，供 SSE 输出带 id 的可恢复事件。
     */
    public static RunEventLiveEvent durable(RunEventSsePayload payload) {
        return new RunEventLiveEvent(true, payload);
    }

    /**
     * 包装实时临时事件，供 SSE 输出不带 id 的高频内容片段。
     */
    public static RunEventLiveEvent transientOnly(RunEventSsePayload payload) {
        return new RunEventLiveEvent(false, payload);
    }
}
