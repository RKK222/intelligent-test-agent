package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证本地 OpenCode 监管器的 PID fencing、端口冲突、重启和 loopback 监听。 */
class OpencodeProcessSupervisorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void refusesToControlReusedPidWithDifferentAuthoritativeStartTime() throws Exception {
        Path executable = Path.of("/bin/sh");
        assumeTrue(Files.isExecutable(executable));
        LocalClientConfiguration configuration = configuration(executable, 24000, 24001);
        LocalClientStateStore stateStore = new LocalClientStateStore(temporaryDirectory.resolve("state"));
        ProcessHandle current = ProcessHandle.current();
        Instant actualStartedAt = current.info().startInstant().orElseThrow();
        stateStore.update(state -> new LocalClientPersistentState(
                state.clientInstanceId(),
                new LocalClientPersistentState.ProcessState(
                        current.pid(), actualStartedAt.minusSeconds(1), executable.toString(), 24000),
                state.workspaces()));

        try (LocalModelRelay relay = new LocalModelRelay(configuration)) {
            OpencodeProcessSupervisor supervisor = new OpencodeProcessSupervisor(configuration, stateStore, relay);

            assertThat(supervisor.status()).satisfies(result -> {
                assertThat(result.success()).isFalse();
                assertThat(result.processStatus()).isEqualTo("FAILED");
                assertThat(result.message()).contains("PID 已被复用");
            });
            assertThat(supervisor.stop()).satisfies(result -> {
                assertThat(result.success()).isFalse();
                assertThat(result.message()).contains("PID 已被复用");
            });
            assertThat(current.isAlive()).isTrue();
        }
    }

    @Test
    void clearsExitedPidEvenWhenAnotherHealthyProcessOwnsTheRecordedPort() throws Exception {
        Path executable = Path.of("/bin/sh");
        assumeTrue(Files.isExecutable(executable));
        long missingPid = 999_999_999L;
        assumeTrue(ProcessHandle.of(missingPid).isEmpty());
        HttpServer unrelated = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        unrelated.createContext("/global/health", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        unrelated.start();
        int occupiedPort = unrelated.getAddress().getPort();
        assumeTrue(occupiedPort < 65_535);
        LocalClientConfiguration configuration = configuration(executable, occupiedPort, occupiedPort + 1);
        LocalClientStateStore stateStore = new LocalClientStateStore(temporaryDirectory.resolve("stale-state"));
        LocalClientPersistentState.ProcessState stale = new LocalClientPersistentState.ProcessState(
                missingPid, Instant.parse("2026-08-22T00:00:00Z"), executable.toString(), occupiedPort);
        stateStore.update(state -> new LocalClientPersistentState(
                state.clientInstanceId(), stale, state.workspaces()));

        try (LocalModelRelay relay = new LocalModelRelay(configuration)) {
            OpencodeProcessSupervisor supervisor = new OpencodeProcessSupervisor(configuration, stateStore, relay);

            assertThat(supervisor.status()).satisfies(result -> {
                assertThat(result.success()).isTrue();
                assertThat(result.processStatus()).isEqualTo("STOPPED");
                assertThat(result.processId()).isNull();
                assertThat(result.opencodeHealthy()).isFalse();
            });
            assertThat(stateStore.read().process()).isNull();

            // 直接 start 也必须越过同一份陈旧记录，而不是返回旧 PID 的永久 FAILED。
            stateStore.update(state -> new LocalClientPersistentState(
                    state.clientInstanceId(), stale, state.workspaces()));
            assertThat(supervisor.start(occupiedPort)).satisfies(result -> {
                assertThat(result.processStatus()).isEqualTo("FAILED");
                assertThat(result.processId()).isNull();
            });
            assertThat(stateStore.read().process()).isNull();
        } finally {
            unrelated.stop(0);
        }
    }

    @Test
    void realOpencodeSkipsOccupiedPortRestartsAndListensOnlyOnIpv4Loopback() throws Exception {
        String executableProperty = System.getProperty("testAgent.localClient.opencodeExecutable");
        assumeTrue(executableProperty != null && !executableProperty.isBlank(),
                "set -DtestAgent.localClient.opencodeExecutable to run the real OpenCode supervisor test");
        Path executable = Path.of(executableProperty).toRealPath();
        assumeTrue(Files.isExecutable(executable));

        try (PortReservation reservation = reservePortRange()) {
            LocalClientConfiguration configuration = configuration(
                    executable, reservation.occupiedPort(), reservation.maxPort());
            LocalClientStateStore stateStore = new LocalClientStateStore(temporaryDirectory.resolve("real-state"));
            try (LocalModelRelay relay = new LocalModelRelay(configuration)) {
                OpencodeProcessSupervisor supervisor = new OpencodeProcessSupervisor(configuration, stateStore, relay);
                try {
                    var started = supervisor.start(reservation.occupiedPort());
                    assertThat(started.success())
                            .as("start result: %s, reservation=%s, relay=%s", started, reservation, relay.baseUrl())
                            .isTrue();
                    assertThat(started.processStatus()).isEqualTo("RUNNING");
                    assertThat(started.opencodePort()).isBetween(
                            reservation.occupiedPort() + 1, reservation.maxPort());
                    assertThat(started.processStartedAt()).isEqualTo(
                            ProcessHandle.of(started.processId()).orElseThrow().info().startInstant().orElseThrow());

                    try (Socket loopback = new Socket()) {
                        loopback.connect(new InetSocketAddress("127.0.0.1", started.opencodePort()), 1000);
                        assertThat(loopback.isConnected()).isTrue();
                    }
                    assertLoopbackOnly(started.processId(), started.opencodePort());

                    long firstPid = started.processId();
                    var restarted = supervisor.restart(started.opencodePort());
                    assertThat(restarted.success()).as("restart result: %s", restarted).isTrue();
                    assertThat(restarted.processStatus()).isEqualTo("RUNNING");
                    assertThat(restarted.processId()).isNotEqualTo(firstPid);
                    assertThat(restarted.opencodePort()).isEqualTo(started.opencodePort());
                } finally {
                    assertThat(supervisor.stop().success()).isTrue();
                }
            }
        }
    }

    private LocalClientConfiguration configuration(Path executable, int portMin, int portMax) {
        return new LocalClientConfiguration(
                URI.create("https://127.0.0.1:65534"),
                URI.create("https://127.0.0.1:65534"),
                "supervisor-test",
                executable,
                temporaryDirectory.resolve("opencode-config"),
                temporaryDirectory.resolve("opencode-data"),
                portMin,
                portMax,
                false);
    }

    private static PortReservation reservePortRange() throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            ServerSocket occupied = new ServerSocket();
            occupied.setReuseAddress(false);
            occupied.bind(new InetSocketAddress("127.0.0.1", 0));
            int first = occupied.getLocalPort();
            if (first > 65435) {
                occupied.close();
                continue;
            }
            return new PortReservation(occupied, first, first + 100);
        }
        throw new IllegalStateException("unable to reserve a loopback test port range");
    }

    private static InetAddress firstNonLoopbackAddress() throws Exception {
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            // macOS VPN 的 utun 点对点地址可能把连接透明重定向到本机 loopback，不能据此判断监听范围。
            if (!network.isUp() || network.isLoopback() || network.isPointToPoint()) continue;
            for (InetAddress address : Collections.list(network.getInetAddresses())) {
                if (!address.isLoopbackAddress()
                        && address.isSiteLocalAddress()
                        && address.getAddress().length == 4) {
                    return address;
                }
            }
        }
        return null;
    }

    private static void assertLoopbackOnly(long processId, int port) throws Exception {
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            Process lsof = new ProcessBuilder(
                            "lsof", "-nP", "-a", "-p", Long.toString(processId),
                            "-iTCP:" + port, "-sTCP:LISTEN")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(lsof.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(lsof.waitFor()).as("lsof output: %s", output).isZero();
            assertThat(output).contains("TCP 127.0.0.1:" + port + " (LISTEN)");
            assertThat(output).doesNotContain("TCP *:" + port);
            return;
        }
        InetAddress nonLoopback = firstNonLoopbackAddress();
        if (nonLoopback == null) {
            return;
        }
        assertThatThrownBy(() -> {
            try (Socket external = new Socket()) {
                external.connect(new InetSocketAddress(nonLoopback, port), 500);
            }
            throw new IllegalStateException(
                    "OpenCode was reachable through non-loopback address " + nonLoopback);
        }).isInstanceOf(java.io.IOException.class);
    }

    private record PortReservation(ServerSocket socket, int occupiedPort, int maxPort)
            implements AutoCloseable {

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
