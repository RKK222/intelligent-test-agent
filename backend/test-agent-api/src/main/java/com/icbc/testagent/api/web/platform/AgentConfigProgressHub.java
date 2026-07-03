package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastHandler;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.AgentConfigOperationStatus;
import com.icbc.testagent.workspace.AgentConfigProgressEvent;
import com.icbc.testagent.workspace.AgentConfigProgressSink;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Agent 配置进度发布中心：业务层写入事件，WebSocket handler 订阅同一 operationId。
 */
@Component
public class AgentConfigProgressHub implements AgentConfigProgressSink, ServerBroadcastHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentConfigProgressHub.class);

    /** 公共 Agent 配置操作进度跨实例广播类型。 */
    public static final String BROADCAST_TYPE = "agent-config.operation-progress";

    private final Map<String, Sinks.Many<AgentConfigProgressEvent>> sinks = new ConcurrentHashMap<>();
    private final ServerBroadcastPublisher broadcastPublisher;
    private final WorkspaceServerIdentity serverIdentity;

    public AgentConfigProgressHub(
            ServerBroadcastPublisher broadcastPublisher,
            WorkspaceServerIdentity serverIdentity) {
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher, "broadcastPublisher must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
    }

    @Override
    public void publish(AgentConfigProgressEvent event) {
        emitLocal(event);
        publishBroadcast(event);
    }

    @Override
    public boolean supports(String type) {
        return BROADCAST_TYPE.equals(type);
    }

    @Override
    public void handle(ServerBroadcastEvent event) {
        if (!supports(event.type()) || broadcastPublisher.instanceId().equals(event.originInstanceId())) {
            return;
        }
        try {
            emitLocal(fromBroadcast(event));
        } catch (RuntimeException exception) {
            LOGGER.warn("Agent 配置进度广播处理失败 eventId={} traceId={}", event.eventId(), event.traceId(), exception);
        }
    }

    Flux<AgentConfigProgressEvent> events(String operationId) {
        return sink(operationId).asFlux();
    }

    private void emitLocal(AgentConfigProgressEvent event) {
        sink(event.operationId()).tryEmitNext(event);
    }

    private void publishBroadcast(AgentConfigProgressEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("operationId", event.operationId());
        payload.put("type", event.type());
        payload.put("status", event.status().name());
        payload.put("step", event.currentStep());
        putIfPresent(payload, "command", event.command());
        putIfPresent(payload, "errorCode", event.errorCode());
        putIfPresent(payload, "errorMessage", event.errorMessage());
        putIfPresent(payload, "commitHash", event.commitHash());
        try {
            broadcastPublisher.publish(new ServerBroadcastEvent(
                    RuntimeIdGenerator.serverBroadcastEventId(),
                    BROADCAST_TYPE,
                    broadcastPublisher.instanceId(),
                    serverIdentity.linuxServerId(),
                    event.traceId(),
                    event.occurredAt(),
                    payload));
        } catch (RuntimeException exception) {
            LOGGER.warn("Agent 配置进度广播发布失败 operationId={} traceId={}", event.operationId(), event.traceId(), exception);
        }
    }

    private AgentConfigProgressEvent fromBroadcast(ServerBroadcastEvent event) {
        Map<String, Object> payload = event.payload();
        return new AgentConfigProgressEvent(
                requiredString(payload, "operationId"),
                requiredString(payload, "type"),
                AgentConfigOperationStatus.valueOf(requiredString(payload, "status")),
                requiredString(payload, "step"),
                optionalString(payload, "command"),
                optionalString(payload, "errorCode"),
                optionalString(payload, "errorMessage"),
                optionalString(payload, "commitHash"),
                event.traceId(),
                occurredAt(event));
    }

    private Instant occurredAt(ServerBroadcastEvent event) {
        Object raw = event.payload().get("occurredAt");
        if (raw instanceof String text && !text.isBlank()) {
            return Instant.parse(text);
        }
        return event.occurredAt();
    }

    private Sinks.Many<AgentConfigProgressEvent> sink(String operationId) {
        return sinks.computeIfAbsent(operationId, ignored -> Sinks.many().multicast().directBestEffort());
    }

    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        String value = optionalString(payload, key);
        if (value == null) {
            throw new IllegalArgumentException("广播载荷缺少字段 " + key);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof String string && !string.isBlank() ? string : null;
    }
}
