package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHeartbeatMaintenanceService;
import com.icbc.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class OpencodeManagerControlConfigTest {

    @TempDir
    private Path tempDir;

    @Test
    void backendLifecycleRunnerAlwaysStartsHeartbeat() {
        BackendJavaProcessLifecycleService lifecycleService = mock(BackendJavaProcessLifecycleService.class);
        OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService =
                mock(OpencodeProcessHeartbeatMaintenanceService.class);
        Path serverIpFile = tempDir.resolve(".serverip");
        ServerIpFileWriter serverIpFileWriter = new ServerIpFileWriter(serverIpFile);
        OpencodeManagerControlConfig.BackendJavaProcessLifecycleRunner runner =
                new OpencodeManagerControlConfig.BackendJavaProcessLifecycleRunner(
                        lifecycleService,
                        heartbeatMaintenanceService,
                        managerControlSettings(),
                        serverIpFileWriter);

        assertThatCode(() -> runner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();

        verify(lifecycleService).registerHeartbeat(org.mockito.ArgumentMatchers.anyString());
        assertThat(serverIpFile).hasContent("10.8.0.21\n");

        runner.destroy();

        verify(lifecycleService).markOffline(org.mockito.ArgumentMatchers.anyString());
    }

    private ManagerControlSettings managerControlSettings() {
        return new ManagerControlSettings(
                "manager-token",
                "http://10.8.0.21:8080",
                new LinuxServerId("10.8.0.21"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(5),
                100);
    }
}
