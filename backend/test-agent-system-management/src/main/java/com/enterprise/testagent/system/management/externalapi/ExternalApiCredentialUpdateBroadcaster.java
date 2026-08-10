package com.enterprise.testagent.system.management.externalapi;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialsUpdatedEvent;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 事务提交后刷新本机外部凭据快照，并通过公共 Redis 广播通知其它 Java。 */
@Service
public class ExternalApiCredentialUpdateBroadcaster implements ServerBroadcastHandler {

    public static final String REFRESH_EVENT_TYPE = "external-api-credential.refresh-requested";
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalApiCredentialUpdateBroadcaster.class);
    private final ServerBroadcastPublisher broadcastPublisher;
    private final BackendInstanceIdentity identity;
    private final ExternalApiCredentialRegistry registry;
    private final Clock clock;

    @Autowired
    public ExternalApiCredentialUpdateBroadcaster(
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity,
            ExternalApiCredentialRegistry registry) {
        this(broadcastPublisher, identity, registry, Clock.systemUTC());
    }

    ExternalApiCredentialUpdateBroadcaster(
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity,
            ExternalApiCredentialRegistry registry,
            Clock clock) {
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher, "broadcastPublisher must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 只有数据库提交成功才使新凭据生效；无事务测试场景使用 fallback 保持语义可验证。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCredentialsUpdated(ExternalApiCredentialsUpdatedEvent event) {
        registry.refresh(event.traceId());
        ServerBroadcastEvent broadcast = new ServerBroadcastEvent(
                RuntimeIdGenerator.serverBroadcastEventId(),
                REFRESH_EVENT_TYPE,
                identity.instanceId(),
                identity.linuxServerId(),
                event.traceId(),
                Instant.now(clock),
                Map.of());
        try {
            broadcastPublisher.publish(broadcast);
        } catch (RuntimeException exception) {
            LOGGER.warn("外部 API 凭据刷新广播发布失败 traceId={}", event.traceId(), exception);
        }
    }

    @Override
    public boolean supports(String type) {
        return REFRESH_EVENT_TYPE.equals(type);
    }

    /** 远端事件只触发本机整表重载，避免广播循环。 */
    @Override
    public void handle(ServerBroadcastEvent event) {
        if (!broadcastPublisher.instanceId().equals(event.originInstanceId())) {
            registry.refresh(event.traceId());
        }
    }
}
