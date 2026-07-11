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
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
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
                        backendSnapshot("bjp_old_backend", "server-b", "http://10.8.0.22:8080", NOW.minusSeconds(30)),
                        backendSnapshot("bjp_new_backend", "server-b", "http://10.8.0.22:18080", NOW)),
                List.of()));

        BackendJavaProcess remote = resolver.requireBackend(new LinuxServerId("server-b"));
        BackendJavaProcess current = resolver.requireBackend(new LinuxServerId("server-a"));

        assertThat(remote.listenUrl()).isEqualTo("http://10.8.0.22:18080");
        assertThat(current.listenUrl()).isEqualTo("http://10.8.0.21:8080");
        assertThat(resolver.currentBackendProcessIdValue()).isEqualTo("bjp_current_backend");
    }

    @Test
    void resolvesRemoteTargetOnlyWhenDifferentFromCurrentServer() {
        BackendJavaRouteResolver resolver = resolver(new FakeHeartbeatStore(List.of(), List.of()));

        assertThat(resolver.remoteTarget(new LinuxServerId("server-a"))).isEmpty();
        assertThat(resolver.remoteTarget(new LinuxServerId("server-b")))
                .contains(new LinuxServerId("server-b"));
    }

    @Test
    void prefersBackendConnectedToManagerEvenWhenAnotherJavaHasLaterHeartbeat() {
        BackendJavaRouteResolver resolver = resolver(new FakeHeartbeatStore(
                List.of(
                        backendSnapshot("bjp_connected", "server-a", "http://10.8.0.21:18080", NOW.minusSeconds(30)),
                        backendSnapshot("bjp_latest", "server-a", "http://10.8.0.21:8080", NOW)),
                List.of(managerSnapshot("ctr_01", "server-a", NOW, "bjp_connected"))),
                new BackendProcessId("bjp_latest"));

        BackendJavaProcess selected = resolver.requireBackend(new LinuxServerId("server-a"));

        assertThat(selected.backendProcessId()).isEqualTo(new BackendProcessId("bjp_connected"));
        assertThat(resolver.remoteTarget(new LinuxServerId("server-a"))).contains(new LinuxServerId("server-a"));
    }

    @Test
    void sameServerDoesNotForwardWhenSelectedBackendIsCurrentJava() {
        BackendJavaRouteResolver resolver = resolver(new FakeHeartbeatStore(
                List.of(backendSnapshot("bjp_connected", "server-a", "http://10.8.0.21:8080", NOW)),
                List.of(managerSnapshot("ctr_01", "server-a", NOW, "bjp_connected"))),
                new BackendProcessId("bjp_connected"));

        assertThat(resolver.requireBackend(new LinuxServerId("server-a")).backendProcessId())
                .isEqualTo(new BackendProcessId("bjp_connected"));
        assertThat(resolver.remoteTarget(new LinuxServerId("server-a"))).isEmpty();
    }

    @Test
    void resolvesContainerOwnerFromLatestManagerSnapshot() {
        BackendJavaRouteResolver resolver = resolver(new FakeHeartbeatStore(
                List.of(),
                List.of(
                        managerSnapshot("ctr_01", "server-b", NOW.minusSeconds(30)),
                        managerSnapshot("ctr_01", "server-c", NOW))));

        Optional<LinuxServerId> linuxServerId = resolver.containerLinuxServerId(new OpencodeContainerId("ctr_01"));

        assertThat(linuxServerId).contains(new LinuxServerId("server-c"));
    }

    @Test
    void missingRemoteBackendThrowsUnifiedUnavailableError() {
        BackendJavaRouteResolver resolver = resolver(new FakeHeartbeatStore(List.of(), List.of()));

        assertThatThrownBy(() -> resolver.requireBackend(new LinuxServerId("missing-server")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
                    assertThat(exception.details()).containsEntry("linuxServerId", "missing-server");
                });
    }

    private static BackendJavaRouteResolver resolver(OpencodeProcessHeartbeatStore heartbeatStore) {
        return resolver(heartbeatStore, new BackendProcessId("bjp_current_backend"));
    }

    private static BackendJavaRouteResolver resolver(
            OpencodeProcessHeartbeatStore heartbeatStore,
            BackendProcessId currentBackendProcessId) {
        return new BackendJavaRouteResolver(
                heartbeatStore,
                new ManagerControlSettings(
                        "manager-token",
                        "http://10.8.0.21:8080",
                        new LinuxServerId("server-a"),
                        "10.8.0.21",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(10),
                        100),
                currentBackendProcessId,
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
        return managerSnapshot(containerId, linuxServerId, heartbeatAt, null);
    }

    private static ManagerRuntimeSnapshot managerSnapshot(
            String containerId,
            String linuxServerId,
            Instant heartbeatAt,
            String connectedBackendProcessId) {
        LinuxServerId serverId = new LinuxServerId(linuxServerId);
        OpencodeContainerId parsedContainerId = new OpencodeContainerId(containerId);
        ContainerManagerId managerId = new ContainerManagerId("mgr_" + linuxServerId.replace(".", "_").replace("-", "_"));
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
                        managerId,
                        parsedContainerId,
                        serverId,
                        "opencode-manager.v1",
                        ManagerConnectionStatus.CONNECTED,
                        Map.of(),
                        heartbeatAt,
                        NOW.minusSeconds(60),
                        heartbeatAt,
                        "trace_manager"),
                connectedBackendProcessId == null ? List.of() : List.of(new OpencodeManagerBackendConnection(
                        managerId,
                        new BackendProcessId(connectedBackendProcessId),
                        ManagerConnectionStatus.CONNECTED,
                        heartbeatAt,
                        heartbeatAt,
                        heartbeatAt,
                        "trace_manager")));
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
