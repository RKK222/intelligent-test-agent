package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.CommonParameterUpdatedEvent;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class OpencodeManagerConfigSyncServiceTest {

    private static final OpencodeContainerId CONTAINER_ID = new OpencodeContainerId("ctr_01");

    @Test
    void currentConfiguredMaxReadsAllPlatformParam() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByEnglishNameAndPlatform("OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL))
                .thenReturn(Optional.of(parameter("8")));
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(repository, mock(ManagerConnectionRegistry.class));

        OptionalInt max = service.currentConfiguredMax();

        assertThat(max.isPresent()).isTrue();
        assertThat(max.getAsInt()).isEqualTo(8);
    }

    @Test
    void currentConfiguredMaxEmptyWhenParamMissingOrNonNumeric() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByEnglishNameAndPlatform("OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL))
                .thenReturn(Optional.empty());
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(repository, mock(ManagerConnectionRegistry.class));

        assertThat(service.currentConfiguredMax().isEmpty()).isTrue();

        when(repository.findByEnglishNameAndPlatform("OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL))
                .thenReturn(Optional.of(parameter("not-a-number")));
        assertThat(service.currentConfiguredMax().isEmpty()).isTrue();
    }

    @Test
    void pushCurrentMaxToSendsConfigUpdateWithConfiguredValue() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByEnglishNameAndPlatform("OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL))
                .thenReturn(Optional.of(parameter("6")));
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(repository, connections);

        service.pushCurrentMaxTo(CONTAINER_ID);

        verify(connections).send(eq(CONTAINER_ID), any(ManagerControlMessage.class));
    }

    @Test
    void pushCurrentMaxToSkipsWhenConfigMissing() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByEnglishNameAndPlatform("OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL))
                .thenReturn(Optional.empty());
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(repository, connections);

        service.pushCurrentMaxTo(CONTAINER_ID);

        verify(connections, never()).send(any(), any());
    }

    @Test
    void pushCurrentMaxToSwallowsSendFailure() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByEnglishNameAndPlatform("OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL))
                .thenReturn(Optional.of(parameter("6")));
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        doThrow(new IllegalStateException("disconnected")).when(connections).send(any(), any());
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(repository, connections);

        // 不应抛异常，避免阻断注册主流程。
        service.pushCurrentMaxTo(CONTAINER_ID);
    }

    @Test
    void broadcastCurrentMaxInvokesRegistryBroadcast() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByEnglishNameAndPlatform("OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL))
                .thenReturn(Optional.of(parameter("8")));
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        when(connections.broadcast(any())).thenReturn(3);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(repository, connections);

        assertThat(service.broadcastCurrentMax()).isEqualTo(3);
        verify(connections).broadcast(any(ManagerControlMessage.class));
    }

    @Test
    void broadcastCurrentMaxSkipsWhenConfigMissing() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByEnglishNameAndPlatform("OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL))
                .thenReturn(Optional.empty());
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(repository, connections);

        assertThat(service.broadcastCurrentMax()).isZero();
        verify(connections, never()).broadcast(any());
    }

    @Test
    void onCommonParameterUpdatedBroadcastsOnlyForMaxProcessesParam() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByEnglishNameAndPlatform("OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL))
                .thenReturn(Optional.of(parameter("8")));
        ManagerConnectionRegistry connections = mock(ManagerConnectionRegistry.class);
        when(connections.broadcast(any())).thenReturn(1);
        OpencodeManagerConfigSyncService service = new OpencodeManagerConfigSyncService(repository, connections);

        service.onCommonParameterUpdated(new CommonParameterUpdatedEvent(
                "OPENCODE_MANAGER_MAX_PROCESSES", ParameterPlatform.ALL, "8", "param_opencode_manager_max_processes", "trace"));
        verify(connections).broadcast(any(ManagerControlMessage.class));

        // 非目标参数不应触发广播，因此 broadcast 仍只被调用一次（上面那次）。
        service.onCommonParameterUpdated(new CommonParameterUpdatedEvent(
                "OPENCODE_WORKSPACE_ROOT", ParameterPlatform.LINUX, "/data", "param_other", "trace"));
        verify(connections, times(1)).broadcast(any());
    }

    private static CommonParameter parameter(String value) {
        return new CommonParameter(
                "param_opencode_manager_max_processes",
                "OPENCODE_MANAGER_MAX_PROCESSES",
                "opencode manager 最大进程数",
                value,
                ParameterPlatform.ALL,
                java.time.Instant.parse("2026-06-27T00:00:00Z"),
                java.time.Instant.parse("2026-06-27T00:00:00Z"));
    }
}
