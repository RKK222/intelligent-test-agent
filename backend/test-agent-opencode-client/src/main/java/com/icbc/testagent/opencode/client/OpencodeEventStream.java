package com.icbc.testagent.opencode.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenCode SSE 建连结果：ready 只在收到 HTTP 响应头后完成，events 再消费同一响应体。
 */
public record OpencodeEventStream(Mono<Void> ready, Flux<JsonNode> events) {

    /** 固化握手与事件两个非空信号。 */
    public OpencodeEventStream {
        Objects.requireNonNull(ready, "ready must not be null");
        Objects.requireNonNull(events, "events must not be null");
    }
}
