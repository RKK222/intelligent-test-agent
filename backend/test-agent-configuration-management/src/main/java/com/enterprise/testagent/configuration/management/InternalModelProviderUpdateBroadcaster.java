package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.InternalModelProvidersReloadedEvent;
import com.enterprise.testagent.domain.configuration.InternalModelProvidersUpdatedEvent;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 内部模型供应商更新广播器，通知所有 Java 进程从数据库重载内存快照。
 */
@Service
public class InternalModelProviderUpdateBroadcaster implements ServerBroadcastHandler {

    public static final String REFRESH_EVENT_TYPE = "internal-model-provider.refresh-requested";
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalModelProviderUpdateBroadcaster.class);

    private final ServerBroadcastPublisher broadcastPublisher;
    private final BackendInstanceIdentity identity;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public InternalModelProviderUpdateBroadcaster(
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity,
            ApplicationEventPublisher eventPublisher) {
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher, "broadcastPublisher must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.clock = Clock.systemUTC();
    }

    @EventListener
    public void onInternalModelProvidersUpdated(InternalModelProvidersUpdatedEvent event) {
        publishBroadcast(event.traceId());
        eventPublisher.publishEvent(new InternalModelProvidersReloadedEvent(event.traceId(), "local-update"));
    }

    @Override
    public boolean supports(String type) {
        return REFRESH_EVENT_TYPE.equals(type);
    }

    @Override
    public void handle(ServerBroadcastEvent event) {
        if (broadcastPublisher.instanceId().equals(event.originInstanceId())) {
            return;
        }
        eventPublisher.publishEvent(new InternalModelProvidersReloadedEvent(event.traceId(), "remote-broadcast"));
    }

    private void publishBroadcast(String traceId) {
        ServerBroadcastEvent event = new ServerBroadcastEvent(
                RuntimeIdGenerator.serverBroadcastEventId(),
                REFRESH_EVENT_TYPE,
                identity.instanceId(),
                identity.linuxServerId(),
                traceId,
                Instant.now(clock),
                Map.of());
        try {
            broadcastPublisher.publish(event);
        } catch (RuntimeException exception) {
            LOGGER.warn("内部模型供应商刷新广播发布失败 traceId={}", traceId, exception);
        }
    }
}
