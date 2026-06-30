package com.icbc.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.CommonParameterReloadedEvent;
import com.icbc.testagent.domain.configuration.CommonParameterUpdatedEvent;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CommonParameterUpdateBroadcasterTest {

    private static final Instant NOW = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void localEventPublishesBroadcastAndReloadedEventWithoutWritingRedisSnapshot() {
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        CommonParameterUpdateBroadcaster broadcaster = new CommonParameterUpdateBroadcaster(
                publisher, identity("instance-a", "srv-a", "bjp_a", "http://a:8080"), eventPublisher, CLOCK);

        broadcaster.onCommonParameterUpdated(new CommonParameterUpdatedEvent(
                "OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL, "8", "param_max", "trace-1"));

        // 发布跨实例广播
        ArgumentCaptor<ServerBroadcastEvent> broadcastCaptor = ArgumentCaptor.forClass(ServerBroadcastEvent.class);
        verify(publisher).publish(broadcastCaptor.capture());
        assertThat(broadcastCaptor.getValue().type()).isEqualTo(CommonParameterUpdateBroadcaster.REFRESH_EVENT_TYPE);
        assertThat(broadcastCaptor.getValue().originInstanceId()).isEqualTo("instance-a");
        assertThat(broadcastCaptor.getValue().payload().get("englishName")).isEqualTo("OPENCODE_MANAGER_MAX_PROCESSES");
        // 发布本地 ReloadedEvent
        ArgumentCaptor<CommonParameterReloadedEvent> reloadedCaptor = ArgumentCaptor.forClass(CommonParameterReloadedEvent.class);
        verify(eventPublisher).publishEvent(reloadedCaptor.capture());
        assertThat(reloadedCaptor.getValue().englishName()).isEqualTo("OPENCODE_MANAGER_MAX_PROCESSES");
    }

    @Test
    void remoteBroadcastPublishesReloadedEventButNoForwardBroadcast() {
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-b");
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        CommonParameterUpdateBroadcaster broadcaster = new CommonParameterUpdateBroadcaster(
                publisher, identity("instance-b", "srv-b", "bjp_b", "http://b:8080"), eventPublisher, CLOCK);

        // 远端事件来源是 instance-a，本机是 instance-b，应处理。
        broadcaster.handle(broadcastEvent("instance-a", "OPENCODE_MANAGER_MAX_PROCESSES", "all", "trace-2"));

        // 不应再转发广播（避免循环）
        verify(publisher, never()).publish(any());
        // 但应发布本地 ReloadedEvent
        verify(eventPublisher).publishEvent(any(CommonParameterReloadedEvent.class));
    }

    @Test
    void remoteBroadcastIgnoresOwnOrigin() {
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        CommonParameterUpdateBroadcaster broadcaster = new CommonParameterUpdateBroadcaster(
                publisher, identity("instance-a", "srv-a", "bjp_a", "http://a:8080"), eventPublisher, CLOCK);

        // 本机回环事件应被忽略
        broadcaster.handle(broadcastEvent("instance-a", "X", "all", "trace-3"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    private static ServerBroadcastEvent broadcastEvent(String origin, String englishName, String platform, String traceId) {
        return new ServerBroadcastEvent(
                "sbe_test",
                CommonParameterUpdateBroadcaster.REFRESH_EVENT_TYPE,
                origin,
                "srv-origin",
                traceId,
                NOW,
                java.util.Map.of(
                        "englishName", englishName,
                        "platform", platform,
                        "parameterId", "param_x",
                        "traceId", traceId));
    }

    private static BackendInstanceIdentity identity(String instanceId, String linuxServerId, String backendProcessId, String listenUrl) {
        return new BackendInstanceIdentity() {
            @Override public String instanceId() { return instanceId; }
            @Override public String linuxServerId() { return linuxServerId; }
            @Override public String backendProcessId() { return backendProcessId; }
            @Override public String listenUrl() { return listenUrl; }
        };
    }
}
