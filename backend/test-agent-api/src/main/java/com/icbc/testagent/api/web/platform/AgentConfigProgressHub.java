package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.workspace.AgentConfigProgressEvent;
import com.icbc.testagent.workspace.AgentConfigProgressSink;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Agent 配置进度发布中心：业务层写入事件，WebSocket handler 订阅同一 operationId。
 */
@Component
public class AgentConfigProgressHub implements AgentConfigProgressSink {

    private final Map<String, Sinks.Many<AgentConfigProgressEvent>> sinks = new ConcurrentHashMap<>();

    @Override
    public void publish(AgentConfigProgressEvent event) {
        sink(event.operationId()).tryEmitNext(event);
    }

    Flux<AgentConfigProgressEvent> events(String operationId) {
        return sink(operationId).asFlux();
    }

    private Sinks.Many<AgentConfigProgressEvent> sink(String operationId) {
        return sinks.computeIfAbsent(operationId, ignored -> Sinks.many().multicast().directBestEffort());
    }
}
