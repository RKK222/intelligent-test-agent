package com.icbc.testagent.configuration.management;

import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastHandler;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshot;
import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshotStore;
import com.icbc.testagent.domain.configuration.CommonParameterReloadedEvent;
import com.icbc.testagent.domain.configuration.CommonParameterUpdatedEvent;
import com.icbc.testagent.domain.configuration.LoadedParameter;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.configuration.ResolvedParameter;
import com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 通用参数内存缓存刷新器：本地参数更新与远端广播都触发 reload，并写出每进程加载快照。
 *
 * <p>事件流（避免循环）：
 * <ul>
 *   <li>本地 {@link CommonParameterUpdatedEvent}：reload + 写快照 + 发布本地 {@link CommonParameterReloadedEvent}
 *       + 发布跨实例广播 {@value #REFRESH_EVENT_TYPE}。</li>
 *   <li>远端广播 {@value #REFRESH_EVENT_TYPE}：仅 reload + 写快照 + 发布本地 {@link CommonParameterReloadedEvent}，
 *       不再转发广播，避免循环。{@link CommonParameterReloadedEvent} 由 opencode-runtime 监听后向本实例 manager 下发。</li>
 * </ul>
 */
@Service
public class CommonParameterCacheRefresher implements ServerBroadcastHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonParameterCacheRefresher.class);

    /** 跨实例通用参数刷新广播事件类型。 */
    public static final String REFRESH_EVENT_TYPE = "common-parameter.refresh-requested";

    private final InMemoryCommonParameterValues values;
    private final ServerBroadcastPublisher broadcastPublisher;
    private final BackendInstanceIdentity identity;
    private final CommonParameterLoadSnapshotStore snapshotStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public CommonParameterCacheRefresher(
            InMemoryCommonParameterValues values,
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity,
            CommonParameterLoadSnapshotStore snapshotStore,
            ApplicationEventPublisher eventPublisher) {
        this(values, broadcastPublisher, identity, snapshotStore, eventPublisher, Clock.systemUTC());
    }

    CommonParameterCacheRefresher(
            InMemoryCommonParameterValues values,
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity,
            CommonParameterLoadSnapshotStore snapshotStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.values = Objects.requireNonNull(values, "values must not be null");
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher, "broadcastPublisher must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 本地通用参数更新：刷新缓存、写快照、通知本实例下游并广播给其他实例。
     */
    @EventListener
    public void onCommonParameterUpdated(CommonParameterUpdatedEvent event) {
        reloadAndRecordSnapshot(event.traceId());
        publishRefreshBroadcast(event.englishName(), event.platform(), event.parameterId(), event.traceId());
        eventPublisher.publishEvent(new CommonParameterReloadedEvent(
                event.englishName(), event.platform(), event.parameterId(), event.traceId(), identity.instanceId()));
    }

    @Override
    public boolean supports(String type) {
        return REFRESH_EVENT_TYPE.equals(type);
    }

    /**
     * 远端广播：刷新缓存、写快照、通知本实例下游；不再转发广播避免循环。
     */
    @Override
    public void handle(ServerBroadcastEvent event) {
        if (broadcastPublisher.instanceId().equals(event.originInstanceId())) {
            return;
        }
        String englishName = stringPayload(event, "englishName");
        String parameterId = stringPayload(event, "parameterId");
        ParameterPlatform platform = parsePlatform(stringPayload(event, "platform"));
        reloadAndRecordSnapshot(event.traceId());
        eventPublisher.publishEvent(new CommonParameterReloadedEvent(
                englishName, platform, parameterId, event.traceId(), identity.instanceId()));
    }

    /**
     * 刷新内存缓存并写出本进程加载快照；供启动加载与事件刷新共用，不发布广播与下游事件。
     */
    public void reloadAndRecordSnapshot(String traceId) {
        try {
            values.reload();
        } catch (RuntimeException exception) {
            LOGGER.warn("通用参数内存缓存刷新失败 traceId={}", traceId, exception);
            return;
        }
        warnUnresolvedReferences(traceId);
        recordLoadSnapshot(traceId);
        LOGGER.info("通用参数内存缓存已刷新 traceId={}", traceId);
    }

    private void warnUnresolvedReferences(String traceId) {
        for (ResolvedParameter parameter : values.resolvedAll()) {
            if (parameter.resolutionError() != null) {
                LOGGER.warn("通用参数解析失败 traceId={} englishName={} platform={} rawValue={} error={}",
                        traceId,
                        parameter.parameter().englishName(),
                        parameter.parameter().platform(),
                        parameter.parameter().parameterValue(),
                        parameter.resolutionError());
            }
        }
    }

    private void recordLoadSnapshot(String traceId) {
        try {
            List<LoadedParameter> parameters = values.resolvedAll().stream()
                    .map(CommonParameterCacheRefresher::toLoadedParameter)
                    .toList();
            CommonParameterLoadSnapshot snapshot = new CommonParameterLoadSnapshot(
                    identity.backendProcessId(),
                    identity.linuxServerId(),
                    identity.listenUrl(),
                    identity.instanceId(),
                    Instant.now(clock),
                    parameters);
            snapshotStore.record(snapshot);
        } catch (RuntimeException exception) {
            // 加载快照仅用于管理端展示，写入失败不应影响参数刷新主流程。
            LOGGER.warn("通用参数加载快照写入失败 traceId={}", traceId, exception);
        }
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
            LOGGER.warn("通用参数刷新广播发布失败 traceId={}", traceId, exception);
        }
    }

    private static LoadedParameter toLoadedParameter(ResolvedParameter resolved) {
        return new LoadedParameter(
                resolved.parameter().englishName(),
                resolved.parameter().platform().value(),
                resolved.parameter().parameterValue(),
                resolved.resolvedValue(),
                resolved.hasReference(),
                resolved.resolutionError());
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
