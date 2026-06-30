package com.icbc.testagent.configuration.management;

import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastHandler;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.CommonParameterReloadedEvent;
import com.icbc.testagent.domain.configuration.CommonParameterUpdatedEvent;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 通用参数更新广播器：参数值修改后通知本实例和其它 Java 实例直接从数据库读取最新值。
 *
 * <p>事件流（避免循环）：
 * <ul>
 *   <li>本地 {@link CommonParameterUpdatedEvent}：发布跨实例广播 {@value #REFRESH_EVENT_TYPE}
 *       + 发布本地 {@link CommonParameterReloadedEvent}。</li>
 *   <li>远端广播 {@value #REFRESH_EVENT_TYPE}：仅发布本地 {@link CommonParameterReloadedEvent}，
 *       不再转发广播。监听方通过 {@code CommonParameterValues} 从数据库读取最新值。</li>
 * </ul>
 */
@Service
public class CommonParameterUpdateBroadcaster implements ServerBroadcastHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonParameterUpdateBroadcaster.class);

    /** 跨实例通用参数更新广播事件类型，沿用历史事件名以保持广播契约兼容。 */
    public static final String REFRESH_EVENT_TYPE = "common-parameter.refresh-requested";

    private final ServerBroadcastPublisher broadcastPublisher;
    private final BackendInstanceIdentity identity;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public CommonParameterUpdateBroadcaster(
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity,
            ApplicationEventPublisher eventPublisher) {
        this(broadcastPublisher, identity, eventPublisher, Clock.systemUTC());
    }

    CommonParameterUpdateBroadcaster(
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher, "broadcastPublisher must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 本地通用参数更新：通知本实例下游并广播给其他实例。
     */
    @EventListener
    public void onCommonParameterUpdated(CommonParameterUpdatedEvent event) {
        publishRefreshBroadcast(event.englishName(), event.platform(), event.parameterId(), event.traceId());
        eventPublisher.publishEvent(new CommonParameterReloadedEvent(
                event.englishName(), event.platform(), event.parameterId(), event.traceId(), identity.instanceId()));
    }

    @Override
    public boolean supports(String type) {
        return REFRESH_EVENT_TYPE.equals(type);
    }

    /**
     * 远端广播：通知本实例下游直接查库；不再转发广播避免循环。
     */
    @Override
    public void handle(ServerBroadcastEvent event) {
        if (broadcastPublisher.instanceId().equals(event.originInstanceId())) {
            return;
        }
        String englishName = stringPayload(event, "englishName");
        String parameterId = stringPayload(event, "parameterId");
        ParameterPlatform platform = parsePlatform(stringPayload(event, "platform"));
        eventPublisher.publishEvent(new CommonParameterReloadedEvent(
                englishName, platform, parameterId, event.traceId(), identity.instanceId()));
    }

    private void publishRefreshBroadcast(
            String englishName, ParameterPlatform platform, String parameterId, String traceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("englishName", englishName);
        if (platform != null) {
            payload.put("platform", platform.value());
        }
        payload.put("parameterId", parameterId);
        payload.put("traceId", traceId);
        ServerBroadcastEvent event = new ServerBroadcastEvent(
                RuntimeIdGenerator.serverBroadcastEventId(),
                REFRESH_EVENT_TYPE,
                identity.instanceId(),
                identity.linuxServerId(),
                traceId,
                Instant.now(clock),
                payload);
        try {
            broadcastPublisher.publish(event);
        } catch (RuntimeException exception) {
            LOGGER.warn("通用参数更新广播发布失败 traceId={}", traceId, exception);
        }
    }

    private static String stringPayload(ServerBroadcastEvent event, String key) {
        Object value = event.payload().get(key);
        return value instanceof String string ? string : null;
    }

    private static ParameterPlatform parsePlatform(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ParameterPlatform.fromValue(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
