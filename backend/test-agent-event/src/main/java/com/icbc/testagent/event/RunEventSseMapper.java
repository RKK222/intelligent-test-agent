package com.icbc.testagent.event;

import com.icbc.testagent.domain.event.RunEvent;
import java.util.Objects;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

/**
 * RunEvent 到 SSE 的协议映射。durable 事件使用 seq 作为可续传 id；transient 实时输出不设置 SSE id。
 */
@Component
public class RunEventSseMapper {

    /**
     * 将持久化 RunEvent 映射成 SSE，使用 seq 作为 SSE id，便于客户端用 Last-Event-ID 续传。
     */
    public ServerSentEvent<RunEventSsePayload> toSse(RunEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return ServerSentEvent.<RunEventSsePayload>builder()
                .id(Long.toString(event.seq()))
                .event(event.type().wireName())
                .data(RunEventSsePayload.from(event))
                .build();
    }

    /**
     * 将 durable payload 映射成带 id 的 SSE；seq 必须大于 0，避免不可恢复事件被误当作续传点。
     */
    public ServerSentEvent<RunEventSsePayload> toDurableSse(RunEventSsePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        if (payload.seq() <= 0) {
            throw new IllegalArgumentException("durable SSE payload seq must be greater than 0");
        }
        return ServerSentEvent.<RunEventSsePayload>builder()
                .id(Long.toString(payload.seq()))
                .event(payload.type())
                .data(payload)
                .build();
    }

    /**
     * 将 transient payload 映射成不带 id 的 SSE；seq 必须为 0，客户端不得把它作为 Last-Event-ID。
     */
    public ServerSentEvent<RunEventSsePayload> toTransientSse(RunEventSsePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        if (payload.seq() != 0) {
            throw new IllegalArgumentException("transient SSE payload seq must be 0");
        }
        return ServerSentEvent.<RunEventSsePayload>builder()
                .event(payload.type())
                .data(payload)
                .build();
    }
}
