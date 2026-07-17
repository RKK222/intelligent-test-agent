package com.enterprise.testagent.domain.opencodeprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpencodeProcessDomainTest {

    private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");

    @Test
    void linuxServerIdAllowsStableServerIdentifiers() {
        LinuxServerId linuxServerId = new LinuxServerId("server-a_01");

        assertThat(linuxServerId.value()).isEqualTo("server-a_01");
        assertThat(new LinuxServerId("prod_01").value()).isEqualTo("prod_01");
        assertThat(new LinuxServerId("_prod-01").value()).isEqualTo("_prod-01");
        assertThat(new LinuxServerId("10.8.0.12").value()).isEqualTo("10.8.0.12");
        assertThatThrownBy(() -> new LinuxServerId(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinuxServerId("server a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinuxServerId("server/a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinuxServerId("server:a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinuxServerId("a".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containerRequiresValidPortRangeAndCapacity() {
        OpencodeContainer container = container();

        assertThat(container.availableCapacity()).isEqualTo(4);
        assertThatThrownBy(() -> new OpencodeContainer(
                        new OpencodeContainerId("ctr_01"),
                        new LinuxServerId("10.8.0.12"),
                        "opencode-a",
                        4100,
                        4096,
                        4,
                        0,
                        OpencodeContainerStatus.READY,
                        NOW,
                        NOW,
                        NOW,
                        "trace_123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpencodeContainer(
                        new OpencodeContainerId("ctr_01"),
                        new LinuxServerId("10.8.0.12"),
                        "opencode-a",
                        4096,
                        4097,
                        3,
                        0,
                        OpencodeContainerStatus.READY,
                        NOW,
                        NOW,
                        NOW,
                        "trace_123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serverProcessBaseUrlOnlyRequiresHttpUrlAndMatchingPort() {
        OpencodeServerProcess process = opencodeProcess();

        assertThat(process.linuxServerId()).isEqualTo(new LinuxServerId("server-a"));
        assertThat(process.baseUrl()).isEqualTo("http://10.8.0.12:4096");
        assertThatThrownBy(() -> new OpencodeServerProcess(
                        new OpencodeProcessId("ocp_01"),
                        new UserId("usr_test"),
                        new LinuxServerId("server-a"),
                        new OpencodeContainerId("ctr_01"),
                        4096,
                        12345L,
                        "http://10.8.0.12:4100",
                        OpencodeServerProcessStatus.RUNNING,
                        "/data/opencode/session/4096",
                        "/data/opencode/.config/opencode/",
                        NOW,
                        NOW,
                        "ok",
                        NOW,
                        NOW,
                        "trace_123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userBindingRequiresOpencodeAgentAndTrace() {
        UserOpencodeProcessBinding binding = new UserOpencodeProcessBinding(
                new UserId("usr_test"),
                " OPENCODE ",
                new OpencodeProcessId("ocp_01"),
                new LinuxServerId("10.8.0.12"),
                4096,
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW,
                NOW,
                "trace_123");

        assertThat(binding.agentId()).isEqualTo("opencode");
        assertThat(binding.port()).isEqualTo(4096);
        assertThatThrownBy(() -> new UserOpencodeProcessBinding(
                        new UserId("usr_test"),
                        "other",
                        new OpencodeProcessId("ocp_01"),
                        new LinuxServerId("10.8.0.12"),
                        4096,
                        UserOpencodeProcessBindingStatus.ACTIVE,
                        NOW,
                        NOW,
                        "trace_123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linuxServerCopiesCapacitySummary() {
        Map<String, Object> capacity = Map.of("containers", 2, "processes", 5);
        LinuxServer server = new LinuxServer(
                new LinuxServerId("10.8.0.12"),
                "backend-a",
                LinuxServerStatus.READY,
                capacity,
                NOW,
                NOW,
                NOW,
                "trace_123");

        assertThat(server.capacitySummary()).containsEntry("containers", 2);
        assertThatThrownBy(() -> server.capacitySummary().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private OpencodeContainer container() {
        return new OpencodeContainer(
                new OpencodeContainerId("ctr_01"),
                new LinuxServerId("10.8.0.12"),
                "opencode-a",
                4096,
                4100,
                4,
                0,
                OpencodeContainerStatus.READY,
                NOW,
                NOW,
                NOW,
                "trace_123");
    }

    private OpencodeServerProcess opencodeProcess() {
        return new OpencodeServerProcess(
                new OpencodeProcessId("ocp_01"),
                new UserId("usr_test"),
                new LinuxServerId("server-a"),
                new OpencodeContainerId("ctr_01"),
                4096,
                12345L,
                "http://10.8.0.12:4096",
                OpencodeServerProcessStatus.RUNNING,
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "ok",
                NOW,
                NOW,
                "trace_123");
    }
}
