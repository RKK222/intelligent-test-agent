package com.icbc.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshot;
import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshotStore;
import com.icbc.testagent.domain.configuration.CommonParameterReloadedEvent;
import com.icbc.testagent.domain.configuration.CommonParameterUpdatedEvent;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.configuration.CommonParameterReferenceResolver;
import com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CommonParameterCacheRefresherTest {

    private static final Instant NOW = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void localEventReloadsPublishesBroadcastAndReloadedEvent() {
        FakeRepository repository = new FakeRepository();
        repository.add(param("OPENCODE_MANAGER_MAX_PROCESSES", "8", ParameterPlatform.ALL));
        InMemoryCommonParameterValues values = new InMemoryCommonParameterValues(repository, new CommonParameterReferenceResolver());
        values.reload();
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        CommonParameterLoadSnapshotStore store = mock(CommonParameterLoadSnapshotStore.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        CommonParameterCacheRefresher refresher = new CommonParameterCacheRefresher(
                values, publisher, identity("instance-a", "srv-a", "bjp_a", "http://a:8080"), store, eventPublisher, CLOCK);

        refresher.onCommonParameterUpdated(new CommonParameterUpdatedEvent(
                "OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL, "8", "param_max", "trace-1"));

        // 发布跨实例广播
        ArgumentCaptor<ServerBroadcastEvent> broadcastCaptor = ArgumentCaptor.forClass(ServerBroadcastEvent.class);
        verify(publisher).publish(broadcastCaptor.capture());
        assertThat(broadcastCaptor.getValue().type()).isEqualTo(CommonParameterCacheRefresher.REFRESH_EVENT_TYPE);
        assertThat(broadcastCaptor.getValue().originInstanceId()).isEqualTo("instance-a");
        assertThat(broadcastCaptor.getValue().payload().get("englishName")).isEqualTo("OPENCODE_MANAGER_MAX_PROCESSES");
        // 发布本地 ReloadedEvent
        ArgumentCaptor<CommonParameterReloadedEvent> reloadedCaptor = ArgumentCaptor.forClass(CommonParameterReloadedEvent.class);
        verify(eventPublisher).publishEvent(reloadedCaptor.capture());
        assertThat(reloadedCaptor.getValue().englishName()).isEqualTo("OPENCODE_MANAGER_MAX_PROCESSES");
        // 写加载快照
        ArgumentCaptor<CommonParameterLoadSnapshot> snapshotCaptor = ArgumentCaptor.forClass(CommonParameterLoadSnapshot.class);
        verify(store).record(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().backendProcessId()).isEqualTo("bjp_a");
        assertThat(snapshotCaptor.getValue().parameters()).hasSize(1);
    }

    @Test
    void remoteBroadcastReloadsAndPublishesReloadedEventButNoForwardBroadcast() {
        FakeRepository repository = new FakeRepository();
        repository.add(param("OPENCODE_MANAGER_MAX_PROCESSES", "8", ParameterPlatform.ALL));
        InMemoryCommonParameterValues values = new InMemoryCommonParameterValues(repository, new CommonParameterReferenceResolver());
        values.reload();
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-b");
        CommonParameterLoadSnapshotStore store = mock(CommonParameterLoadSnapshotStore.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        CommonParameterCacheRefresher refresher = new CommonParameterCacheRefresher(
                values, publisher, identity("instance-b", "srv-b", "bjp_b", "http://b:8080"), store, eventPublisher, CLOCK);

        // 远端事件来源是 instance-a，本机是 instance-b，应处理。
        refresher.handle(broadcastEvent("instance-a", "OPENCODE_MANAGER_MAX_PROCESSES", "all", "trace-2"));

        // 不应再转发广播（避免循环）
        verify(publisher, never()).publish(any());
        // 但应发布本地 ReloadedEvent
        verify(eventPublisher).publishEvent(any(CommonParameterReloadedEvent.class));
        verify(store).record(any());
    }

    @Test
    void remoteBroadcastIgnoresOwnOrigin() {
        FakeRepository repository = new FakeRepository();
        InMemoryCommonParameterValues values = new InMemoryCommonParameterValues(repository, new CommonParameterReferenceResolver());
        values.reload();
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        CommonParameterLoadSnapshotStore store = mock(CommonParameterLoadSnapshotStore.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        CommonParameterCacheRefresher refresher = new CommonParameterCacheRefresher(
                values, publisher, identity("instance-a", "srv-a", "bjp_a", "http://a:8080"), store, eventPublisher, CLOCK);

        // 本机回环事件应被忽略
        refresher.handle(broadcastEvent("instance-a", "X", "all", "trace-3"));

        verify(eventPublisher, never()).publishEvent(any());
        verify(store, never()).record(any());
    }

    private static ServerBroadcastEvent broadcastEvent(String origin, String englishName, String platform, String traceId) {
        return new ServerBroadcastEvent(
                "sbe_test",
                CommonParameterCacheRefresher.REFRESH_EVENT_TYPE,
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

    private static CommonParameter param(String englishName, String value, ParameterPlatform platform) {
        return new CommonParameter(
                "param_" + englishName.toLowerCase(),
                englishName,
                englishName + " 中文名",
                value,
                platform,
                NOW,
                NOW);
    }

    private static final class FakeRepository implements com.icbc.testagent.domain.configuration.CommonParameterRepository {
        private final List<CommonParameter> parameters = new ArrayList<>();

        void add(CommonParameter parameter) {
            parameters.add(parameter);
        }

        @Override
        public Optional<CommonParameter> findByEnglishNameAndPlatform(String englishName, ParameterPlatform platform) {
            return parameters.stream()
                    .filter(p -> p.englishName().equals(englishName) && p.platform() == platform)
                    .findFirst();
        }

        @Override
        public List<CommonParameter> findAll() {
            return List.copyOf(parameters);
        }
    }
}
