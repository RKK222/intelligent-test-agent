package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.net.LinuxServerIpResolver;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHeartbeatMaintenanceService;
import com.icbc.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class OpencodeManagerControlConfigTest {

    @TempDir
    private Path tempDir;

    @Test
    void managerControlSettingsSeparatesStableServerIdAndAdvertisedHost() {
        TestAgentRuntimeProperties properties = new TestAgentRuntimeProperties();
        properties.getOpencode().getManagerControl().setToken("manager-token");
        properties.getOpencode().getManagerControl().setHeartbeatInterval(Duration.ofSeconds(4));
        ServerIdentityResolver serverIdentityResolver = new ServerIdentityResolver(
                Map.of(ServerIdentityResolver.SERVER_ID_ENV, "linux-prod-a"),
                () -> "ignored-hostname");
        ServerAdvertisedHostResolver advertisedHostResolver = new ServerAdvertisedHostResolver(
                Map.of(ServerAdvertisedHostResolver.ADVERTISED_HOST_ENV, "10.8.0.21"),
                detectedIpResolver("10.8.0.99"));

        ManagerControlSettings settings = new OpencodeManagerControlConfig()
                .managerControlSettings(properties, serverIdentityResolver, advertisedHostResolver, 18080);

        assertThat(settings.listenUrl()).isEqualTo("http://10.8.0.21:18080");
        assertThat(settings.linuxServerId()).isEqualTo(new LinuxServerId("linux-prod-a"));
        assertThat(settings.advertisedHost()).isEqualTo("10.8.0.21");
        assertThat(settings.token()).isEqualTo("manager-token");
        assertThat(settings.heartbeatInterval()).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void serverIdentityResolverFallsBackToMachineNameWhenEnvIsMissing() {
        ServerIdentityResolver serverIdentityResolver = new ServerIdentityResolver(Map.of(), () -> "prod-host-01.local");

        assertThat(serverIdentityResolver.resolve()).isEqualTo(new LinuxServerId("prod-host-01.local"));
    }

    @Test
    void advertisedHostResolverFallsBackToDetectedIpWhenEnvIsMissing() {
        ServerAdvertisedHostResolver resolver = new ServerAdvertisedHostResolver(Map.of(), detectedIpResolver("10.8.0.21"));

        assertThat(resolver.resolve()).isEqualTo("10.8.0.21");
    }

    @Test
    void backendLifecycleRunnerWritesServerIdentityAndHostFilesBeforeHeartbeat() {
        BackendJavaProcessLifecycleService lifecycleService = mock(BackendJavaProcessLifecycleService.class);
        OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService =
                mock(OpencodeProcessHeartbeatMaintenanceService.class);
        Path serverIdFile = tempDir.resolve(".serverid");
        Path serverHostFile = tempDir.resolve(".serverhost");
        ServerIdentityFileWriter serverIdentityFileWriter = new ServerIdentityFileWriter(serverIdFile, serverHostFile);
        OpencodeManagerControlConfig.BackendJavaProcessLifecycleRunner runner =
                new OpencodeManagerControlConfig.BackendJavaProcessLifecycleRunner(
                        lifecycleService,
                        heartbeatMaintenanceService,
                        managerControlSettings(),
                        serverIdentityFileWriter);

        assertThatCode(() -> runner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();

        verify(lifecycleService).registerHeartbeat(org.mockito.ArgumentMatchers.anyString());
        assertThat(serverIdFile).hasContent("server-a\n");
        assertThat(serverHostFile).hasContent("10.8.0.21\n");

        runner.destroy();

        verify(lifecycleService).markOffline(org.mockito.ArgumentMatchers.anyString());
    }

    private ManagerControlSettings managerControlSettings() {
        return new ManagerControlSettings(
                "manager-token",
                "http://10.8.0.21:8080",
                new LinuxServerId("server-a"),
                "10.8.0.21",
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(5),
                100);
    }

    private LinuxServerIpResolver detectedIpResolver(String ip) {
        LinuxServerIpResolver linuxServerIpResolver = mock(LinuxServerIpResolver.class);
        when(linuxServerIpResolver.resolve()).thenReturn(ip);
        return linuxServerIpResolver;
    }
}
