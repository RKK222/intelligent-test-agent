package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.event.RunEventDraft;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** AgentRuntime 的事件握手契约；ready 完成后才允许触发可能极速结束的远端 Run。 */
public record AgentEventStream(Mono<Void> ready, Flux<RunEventDraft> events) {

    /** 固化握手与事件两个非空信号。 */
    public AgentEventStream {
        Objects.requireNonNull(ready, "ready must not be null");
        Objects.requireNonNull(events, "events must not be null");
    }
}
