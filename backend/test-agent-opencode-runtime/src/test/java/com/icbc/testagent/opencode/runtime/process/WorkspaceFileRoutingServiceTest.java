package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkspaceFileRoutingServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-26T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void routesWorkspaceFilesToBackendOnSameLinuxServer() {
        WorkspaceRepository workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        OpencodeProcessHeartbeatStore heartbeatStore = Mockito.mock(OpencodeProcessHeartbeatStore.class);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace("10.8.0.12")));
        when(assignmentService.status(USER_ID, "opencode", TRACE_ID)).thenReturn(process("10.8.0.12"));
        when(heartbeatStore.liveBackendSnapshots()).thenReturn(List.of(backendSnapshot("10.8.0.12")));

        WorkspaceFileRouteResponse response = service(workspaceRepository, assignmentService, heartbeatStore)
                .routeWorkspace(USER_ID, "opencode", WORKSPACE_ID, TRACE_ID);

        assertThat(response.linuxServerId()).isEqualTo("10.8.0.12");
        assertThat(response.baseUrl()).isEqualTo("http://10.8.0.12:8080");
        assertThat(response.webSocketPath()).isEqualTo(WorkspaceFileRoutingService.WEB_SOCKET_PATH);
    }

    @Test
    void rejectsWorkspaceFilesWhenWorkspaceAndAgentAreOnDifferentLinuxServers() {
        WorkspaceRepository workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        OpencodeProcessHeartbeatStore heartbeatStore = Mockito.mock(OpencodeProcessHeartbeatStore.class);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace("10.8.0.12")));
        when(assignmentService.status(eq(USER_ID), eq("opencode"), eq(TRACE_ID))).thenReturn(process("10.8.0.13"));

        assertThatThrownBy(() -> service(workspaceRepository, assignmentService, heartbeatStore)
                        .routeWorkspace(USER_ID, "opencode", WORKSPACE_ID, TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    private static WorkspaceFileRoutingService service(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        return new WorkspaceFileRoutingService(
                workspaceRepository,
                assignmentService,
                heartbeatStore,
                new ManagerControlSettings(
                        "secret-token",
                        "http://10.8.0.12:8080",
                        new LinuxServerId("10.8.0.12"),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(10),
                        100),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Workspace workspace(String linuxServerId) {
        return new Workspace(
                WORKSPACE_ID,
                "Demo",
                "/tmp/demo",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                linuxServerId,
                TRACE_ID);
    }

    private static UserOpencodeProcessStatusResponse process(String linuxServerId) {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.READY,
                false,
                "ready",
                "ocp_1234567890abcdef",
                linuxServerId,
                "ctr_01",
                4096,
                "http://" + linuxServerId + ":4096",
                NOW);
    }

    private static BackendJavaProcess backend(String linuxServerId) {
        return new BackendJavaProcess(
                new BackendProcessId("bjp_1234567890abcdef"),
                new LinuxServerId(linuxServerId),
                "http://" + linuxServerId + ":8080",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static BackendRuntimeSnapshot backendSnapshot(String linuxServerId) {
        return new BackendRuntimeSnapshot(
                new LinuxServer(
                        new LinuxServerId(linuxServerId),
                        linuxServerId,
                        LinuxServerStatus.READY,
                        Map.of("backendWorkingDirectory", "/workspace/" + linuxServerId),
                        NOW,
                        NOW,
                        NOW,
                        TRACE_ID),
                backend(linuxServerId));
    }
}
