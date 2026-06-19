package com.example.testagent.event;

import com.example.testagent.domain.event.RunEvent;
import java.util.Objects;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

/**
 * RunEvent 到 SSE 的协议映射，固定使用 seq 作为可续传 id，wireName 作为 SSE event。
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
}
