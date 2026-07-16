package com.enterprise.testagent.opencode.client;

import com.enterprise.testagent.domain.event.RunEventDraft;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** OpenCode 事件映射后的稳定流契约，同时暴露真实 SSE 握手完成信号。 */
public record OpencodeRunEventStream(Mono<Void> ready, Flux<RunEventDraft> events) {

    /** 固化握手与事件两个非空信号。 */
    public OpencodeRunEventStream {
        Objects.requireNonNull(ready, "ready must not be null");
        Objects.requireNonNull(events, "events must not be null");
    }
}
