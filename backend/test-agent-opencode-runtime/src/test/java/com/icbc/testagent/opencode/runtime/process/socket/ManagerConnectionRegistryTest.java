package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
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
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
    }
}
