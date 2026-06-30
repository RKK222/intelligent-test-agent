package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BackendJavaRouteResolverTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");

    @Test
    void resolvesLatestBackendByLinuxServerIdAndKeepsCurrentFallback() {
        BackendJavaRouteResolver resolver = resolver(new FakeHeartbeatStore(
                List.of(
                        backendSnapshot("bjp_old_backend", "10.8.0.22", "http://10.8.0.22:8080", NOW.minusSeconds(30)),
                        backendSnapshot("bjp_new_backend", "10.8.0.22", "http://10.8.0.22:18080", NOW)),
                List.of()));

        BackendJavaProcess remote = resolver.requireBackend(new LinuxServerId("10.8.0.22"));
        BackendJavaProcess current = resolver.requireBackend(new LinuxServerId("10.8.0.21"));

        assertThat(remote.listenUrl()).isEqualTo("http://10.8.0.22:18080");
        assertThat(current.listenUrl()).isEqualTo("http://10.8.0.21:8080");
    }

    @Test
    void resolvesRemoteTargetOnlyWhenDifferentFromCurrentServer() {
        BackendJavaRouteResolver resolver = resolver(new FakeHeartbeatStore(List.of(), List.of()));

        assertThat(resolver.remoteTarget(new LinuxServerId("10.8.0.21"))).isEmpty();
        assertThat(resolver.remoteTarget(new LinuxServerId("10.8.0.22")))
                .contains(new LinuxServerId("10.8.0.22"));
    }

    @Test
    void resolvesContainerOwnerFromLatestManagerSnapshot() {
        BackendJavaRouteResolver resolver = resolver(new FakeHeartbeatStore(
                List.of(),
                List.of(
                        managerSnapshot("ctr_01", "10.8.0.22", NOW.minusSeconds(30)),
                        managerSnapshot("ctr_01", "10.8.0.23", NOW))));

        Optional<LinuxServerId> linuxServerId = resolver.containerLinuxServerId(new OpencodeContainerId("ctr_01"));

        assertThat(linuxServerId).contains(new LinuxServerId("10.8.0.23"));
    }

    @Test
    void missingRemoteBackendThrowsUnifiedUnavailableError() {
        BackendJavaRouteResolver resolver = resolver(new FakeHeartbeatStore(List.of(), List.of()));

        assertThatThrownBy(() -> resolver.requireBackend(new LinuxServerId("10.8.0.99")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
                    assertThat(exception.details()).containsEntry("linuxServerId", "10.8.0.99");
                });
    }

    private static BackendJavaRouteResolver resolver(OpencodeProcessHeartbeatStore heartbeatStore) {
        return new BackendJavaRouteResolver(
                heartbeatStore,
                new ManagerControlSettings(
                        "manager-token",
                        "http://10.8.0.21:8080",
                        new LinuxServerId("10.8.0.21"),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(10),
                        100),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private static BackendRuntimeSnapshot backendSnapshot(
            String backendProcessId,
            String linuxServerId,
            String listenUrl,
            Instant heartbeatAt) {
        LinuxServerId serverId = new LinuxServerId(linuxServerId);
        return new BackendRuntimeSnapshot(
                new LinuxServer(
                        serverId,
                        linuxServerId,
                        LinuxServerStatus.READY,
                        Map.of("backendListenUrl", listenUrl),
                        heartbeatAt,
                        NOW.minusSeconds(60),
                        heartbeatAt,
                        "trace_backend"),
                new BackendJavaProcess(
                        new BackendProcessId(backendProcessId),
                        serverId,
                        listenUrl,
                        BackendJavaProcessStatus.READY,
                        NOW.minusSeconds(60),
                        heartbeatAt,
                        NOW.minusSeconds(60),
                        heartbeatAt,
                        "trace_backend"));
    }

    private static ManagerRuntimeSnapshot managerSnapshot(String containerId, String linuxServerId, Instant heartbeatAt) {
        LinuxServerId serverId = new LinuxServerId(linuxServerId);
        OpencodeContainerId parsedContainerId = new OpencodeContainerId(containerId);
        return new ManagerRuntimeSnapshot(
                new OpencodeContainer(
                        parsedContainerId,
                        serverId,
                        "opencode-" + containerId,
                        4096,
                        4105,
                        10,
                        1,
                        OpencodeContainerStatus.READY,
                        heartbeatAt,
                        NOW.minusSeconds(60),
                        heartbeatAt,
                        "trace_manager"),
                new OpencodeContainerManager(
                        new ContainerManagerId("mgr_" + linuxServerId.replace(".", "")),
                        parsedContainerId,
                        serverId,
                        "opencode-manager.v1",
                        ManagerConnectionStatus.CONNECTED,
                        Map.of(),
                        heartbeatAt,
                        NOW.minusSeconds(60),
                        heartbeatAt,
                        "trace_manager"),
                List.of());
    }

    private record FakeHeartbeatStore(
            List<BackendRuntimeSnapshot> backendSnapshots,
            List<ManagerRuntimeSnapshot> managerSnapshots) implements OpencodeProcessHeartbeatStore {
        @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) { }
        @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { }
        @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { }
        @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
        @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return backendSnapshots; }
        @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return managerSnapshots; }
        @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
        @Override public void cleanupExpiredHeartbeats() { }
    }
}
