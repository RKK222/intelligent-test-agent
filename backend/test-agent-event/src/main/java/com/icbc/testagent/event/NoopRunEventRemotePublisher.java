package com.icbc.testagent.event;

import com.icbc.testagent.domain.run.RunId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 默认远端广播空实现，Redis 广播未启用或不可用时保持既有单机 live bus 行为。
 */
@Component
@ConditionalOnMissingBean(RunEventRemotePublisher.class)
public final class NoopRunEventRemotePublisher implements RunEventRemotePublisher {

    public static final NoopRunEventRemotePublisher INSTANCE = new NoopRunEventRemotePublisher();

    public NoopRunEventRemotePublisher() {}

    @Override
    public void publish(RunEventLiveEvent event) {
        // Redis 广播是补充通道，关闭时无需做任何操作。
    }

    @Override
    public Flux<RunEventLiveEvent> stream(RunId runId) {
        return Flux.empty();
    }
}
