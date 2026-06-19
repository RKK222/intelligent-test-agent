package com.example.testagent.event;

import com.example.testagent.domain.event.RunEvent;
import java.util.Objects;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

/**
 * RunEvent 到 SSE 的协议映射。durable 事件使用 seq 作为可续传 id；transient 实时输出不设置 SSE id。
 */
@Component
public class RunEventSseMapper {

    public ServerSentEvent<RunEventSsePayload> toSse(RunEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return ServerSentEvent.<RunEventSsePayload>builder()
                .id(Long.toString(event.seq()))
                .event(event.type().wireName())
                .data(RunEventSsePayload.from(event))
                .build();
    }

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
