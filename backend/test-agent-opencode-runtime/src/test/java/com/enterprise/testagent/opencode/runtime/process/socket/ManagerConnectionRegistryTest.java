package com.enterprise.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManagerConnectionRegistryTest {

    @Test
    void routesCommandByContainerAndDisconnectsCleanly() {
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        List<ManagerControlMessage> sent = new ArrayList<>();
        OpencodeContainerId containerId = new OpencodeContainerId("ctr_01");

        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                containerId,
                new BackendProcessId("bjp_1234567890abcdef"),
                sent::add);

        registry.send(containerId, ManagerControlMessage.command(
                "mcmd_1234567890abcdef",
                "start",
                4096,
                5000,
                "trace_1234567890abcdef"));

        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().command()).isEqualTo("start");

        registry.disconnect(containerId);

        assertThatThrownBy(() -> registry.send(containerId, sent.getFirst()))
                .isInstanceOf(ManagerCommandNotDispatchedException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
    }

    @Test
    void broadcastReachesAllConnectionsAndSurvivesSenderFailure() {
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        List<ManagerControlMessage> firstSent = new ArrayList<>();
        List<ManagerControlMessage> secondSent = new ArrayList<>();
        OpencodeContainerId containerA = new OpencodeContainerId("ctr_a");
        OpencodeContainerId containerB = new OpencodeContainerId("ctr_b");

        registry.register(new ContainerManagerId("mgr_a"), containerA, new BackendProcessId("bjp_a"), firstSent::add);
        // 第二个 sender 故意抛异常，验证不中断其余连接广播。
        registry.register(new ContainerManagerId("mgr_b"), containerB, new BackendProcessId("bjp_b"), message -> {
            throw new IllegalStateException("sink closed");
        });

        ManagerControlMessage message = ManagerControlMessage.configUpdate(8, "trace_broadcast");
        int sent = registry.broadcast(message);

        assertThat(sent).isEqualTo(1);
        assertThat(firstSent).containsExactly(message);
        assertThat(secondSent).isEmpty();
        assertThat(registry.connectedContainerIds()).containsExactlyInAnyOrder(containerA, containerB);
    }
}
