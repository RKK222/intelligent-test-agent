package com.enterprise.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterReloadedEvent;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class OpencodeManagerConfigSyncServiceTest {

    private static final OpencodeContainerId CONTAINER_ID = new OpencodeContainerId("ctr_01");

    @Test
    void currentConfiguredMaxReadsAllPlatformParam() {
        CommonParameterValues values = values(Map.of("OPENCODE_MANAGER_MAX_PROCESSES", "8"));
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(values, mock(ManagerConnectionRegistry.class));

        OptionalInt max = service.currentConfiguredMax();

        assertThat(max.isPresent()).isTrue();
        assertThat(max.getAsInt()).isEqualTo(8);
    }

    @Test
    void currentConfiguredMaxEmptyWhenParamMissingOrNonNumeric() {
        CommonParameterValues values = values(Map.of());
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(values, mock(ManagerConnectionRegistry.class));

        assertThat(service.currentConfiguredMax().isEmpty()).isTrue();

        CommonParameterValues nonNumeric = values(Map.of("OPENCODE_MANAGER_MAX_PROCESSES", "not-a-number"));
        assertThat(new OpencodeManagerConfigSyncService(nonNumeric, mock(ManagerConnectionRegistry.class))
                .currentConfiguredMax().isEmpty()).isTrue();
    }

    @Test
    void pushCurrentMaxToSendsOnlyMaxProcessesForRuntimeRefresh() {
        CommonParameterValues values = values(Map.of(
                "OPENCODE_MANAGER_MAX_PROCESSES", "6",
                "OPENCODE_SESSION_DIR", "/data/.testagent/agent-opencode/.session/",
                "OPENCODE_PUBLIC_CONFIG_DIR", "/data/.testagent/agent-opencode/.config/opencode/"));
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(values, connections);

        service.pushCurrentMaxTo(CONTAINER_ID);

        ArgumentCaptor<ManagerControlMessage> captor = ArgumentCaptor.forClass(ManagerControlMessage.class);
        verify(connections).send(eq(CONTAINER_ID), captor.capture());
        assertThat(captor.getValue().maxProcesses()).isEqualTo(6);
        assertThat(captor.getValue().sessionRoot()).isNull();
        assertThat(captor.getValue().configDir()).isNull();
    }

    @Test
    void pushCurrentMaxToSendsSafeErrorWhenConfigMissing() {
        CommonParameterValues values = values(Map.of());
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(values, connections);

        service.pushCurrentMaxTo(CONTAINER_ID);

        ArgumentCaptor<ManagerControlMessage> captor = ArgumentCaptor.forClass(ManagerControlMessage.class);
        verify(connections).send(eq(CONTAINER_ID), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ManagerControlProtocol.TYPE_ERROR);
        assertThat(captor.getValue().errorCode()).isEqualTo("OPENCODE_UNAVAILABLE");
    }

    @Test
    void pushCurrentMaxToSwallowsSendFailure() {
        CommonParameterValues values = values(Map.of("OPENCODE_MANAGER_MAX_PROCESSES", "6"));
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        doThrow(new IllegalStateException("disconnected")).when(connections).send(any(), any());
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(values, connections);

        // 不应抛异常，避免阻断注册主流程。
        service.pushCurrentMaxTo(CONTAINER_ID);
    }

    @Test
    void broadcastCurrentMaxInvokesRegistryBroadcast() {
        CommonParameterValues values = values(Map.of(
                "OPENCODE_MANAGER_MAX_PROCESSES", "8",
                "OPENCODE_SESSION_DIR", "/data/session/",
                "OPENCODE_PUBLIC_CONFIG_DIR", "/data/config/opencode/"));
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        when(connections.broadcast(any())).thenReturn(3);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(values, connections);

        assertThat(service.broadcastCurrentMax()).isEqualTo(3);
        verify(connections).broadcast(any(ManagerControlMessage.class));
    }

    @Test
    void broadcastCurrentMaxSkipsWhenConfigMissing() {
        CommonParameterValues values = values(Map.of());
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(values, connections);

        assertThat(service.broadcastCurrentMax()).isZero();
        verify(connections, never()).broadcast(any());
    }

    @Test
    void onReloadedBroadcastsOnlyForManagerRuntimeParams() {
        CommonParameterValues values = values(Map.of(
                "OPENCODE_MANAGER_MAX_PROCESSES", "8",
                "OPENCODE_SESSION_DIR", "/data/session/",
                "OPENCODE_PUBLIC_CONFIG_DIR", "/data/config/opencode/"));
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        when(connections.broadcast(any())).thenReturn(1);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(values, connections);

        service.onCommonParameterReloaded(reloaded("OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL));
        verify(connections).broadcast(any(ManagerControlMessage.class));

        // 非目标参数不应触发广播，因此 broadcast 仍只被调用一次（上面那次）。
        service.onCommonParameterReloaded(reloaded("OPENCODE_WORKSPACE_ROOT", ParameterPlatform.LINUX));
        verify(connections, times(1)).broadcast(any());
    }

    @Test
    void onReloadedDoesNotBroadcastForSessionAndConfigParams() {
        CommonParameterValues values = values(Map.of(
                "OPENCODE_MANAGER_MAX_PROCESSES", "8",
                "OPENCODE_SESSION_DIR", "/data/session/",
                "OPENCODE_PUBLIC_CONFIG_DIR", "/data/config/opencode/"));
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        when(connections.broadcast(any())).thenReturn(1);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(values, connections);

        service.onCommonParameterReloaded(reloaded("OPENCODE_SESSION_DIR", ParameterPlatform.current()));
        service.onCommonParameterReloaded(reloaded("OPENCODE_PUBLIC_CONFIG_DIR", ParameterPlatform.current()));

        verify(connections, never()).broadcast(any());
    }

    @Test
    void configUpdateMessageEmptyWhenRequiredPathMissing() {
        CommonParameterValues values = values(Map.of("OPENCODE_MANAGER_MAX_PROCESSES", "8"));
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(values, connections);

        assertThat(service.configUpdateMessage("trace_config").isEmpty()).isTrue();
        service.pushCurrentMaxTo(CONTAINER_ID);

        verify(connections, atLeastOnce()).send(eq(CONTAINER_ID), any(ManagerControlMessage.class));
    }

    private static CommonParameterReloadedEvent reloaded(String englishName, ParameterPlatform platform) {
        return new CommonParameterReloadedEvent(englishName, platform, "param_" + englishName.toLowerCase(), "trace", "instance-test");
    }

    private static CommonParameterValues values(Map<String, String> resolved) {
        Map<String, String> copy = new HashMap<>(resolved);
        return new CommonParameterValues() {
            @Override
            public Optional<String> resolvedValue(String englishName) {
                return Optional.ofNullable(copy.get(englishName));
            }

            @Override
            public Optional<String> resolvedValue(String englishName, ParameterPlatform platform) {
                return Optional.ofNullable(copy.get(englishName));
            }

            @Override
            public Optional<CommonParameter> raw(String englishName, ParameterPlatform platform) {
                return Optional.empty();
            }

            @Override
            public List<CommonParameter> findAll() {
                return List.of();
            }

            @Override
            public List<ResolvedParameter> resolvedAll() {
                return List.of();
            }
        };
    }
}
