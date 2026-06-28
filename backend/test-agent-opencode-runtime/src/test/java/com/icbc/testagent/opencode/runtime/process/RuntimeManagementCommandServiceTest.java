package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeManagementCommandServiceTest {

    @Test
    void restartManagedProcessDelegatesToGatewayByContainerAndPort() {
        RecordingGateway gateway = new RecordingGateway();
        RuntimeManagementCommandService service = new RuntimeManagementCommandService(gateway);

        OpencodeProcessControlResult result = service.restartManagedProcess(
                new OpencodeContainerId("ctr_01"),
                4096,
                "trace_1234567890abcdef");

        assertThat(gateway.restartCommands).singleElement().satisfies(command -> {
            assertThat(command.containerId()).isEqualTo(new OpencodeContainerId("ctr_01"));
            assertThat(command.port()).isEqualTo(4096);
            assertThat(command.traceId()).isEqualTo("trace_1234567890abcdef");
        });
        assertThat(result.command()).isEqualTo("restart");
        assertThat(result.status()).isEqualTo("STARTED");
    }

    @Test
    void stopManagedProcessDelegatesToGatewayByContainerAndPort() {
        RecordingGateway gateway = new RecordingGateway();
        RuntimeManagementCommandService service = new RuntimeManagementCommandService(gateway);

        OpencodeProcessControlResult result = service.stopManagedProcess(
                new OpencodeContainerId("ctr_01"),
                4097,
                "trace_1234567890abcdef");

        assertThat(gateway.stopCommands).singleElement().satisfies(command -> {
            assertThat(command.containerId()).isEqualTo(new OpencodeContainerId("ctr_01"));
            assertThat(command.port()).isEqualTo(4097);
            assertThat(command.traceId()).isEqualTo("trace_1234567890abcdef");
        });
        assertThat(result.command()).isEqualTo("stop");
        assertThat(result.status()).isEqualTo("STOPPED");
    }

    private static final class RecordingGateway implements OpencodeProcessManagerGateway {
        private final List<OpencodeProcessControlCommand> restartCommands = new ArrayList<>();
        private final List<OpencodeProcessControlCommand> stopCommands = new ArrayList<>();

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            throw new UnsupportedOperationException("checkHealth is not used");
        }

        @Override
        public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) {
            throw new UnsupportedOperationException("startProcess is not used");
        }

        @Override
        public OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command) {
            restartCommands.add(command);
            return new OpencodeProcessControlResult(
                    "restart",
                    "STARTED",
                    command.port(),
                    12345L,
                    "http://10.8.0.12:4096",
                    "/data/opencode/session/4096",
                    "/data/opencode/.config/opencode/",
                    true,
                    "opencode server started",
                    command.traceId());
        }

        @Override
        public OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command) {
            stopCommands.add(command);
            return new OpencodeProcessControlResult(
                    "stop",
                    "STOPPED",
                    command.port(),
                    22345L,
                    "http://10.8.0.12:4097",
                    "/data/opencode/session/4097",
                    "/data/opencode/.config/opencode/",
                    true,
                    "opencode server stopped",
                    command.traceId());
        }
    }
}
