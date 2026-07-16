package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextWorkspaceMutation;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkspaceFileRoutingServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-26T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @TempDir
    Path root;

    @Test
    void routesWorkspaceFilesToBackendOnSameLinuxServer() {
        WorkspaceRepository workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        OpencodeProcessHeartbeatStore heartbeatStore = Mockito.mock(OpencodeProcessHeartbeatStore.class);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace("10.8.0.12")));
        when(assignmentService.fileRoutingAffinity(USER_ID, "opencode", TRACE_ID)).thenReturn(affinity("10.8.0.12"));
        when(heartbeatStore.liveBackendSnapshots()).thenReturn(List.of(backendSnapshot("10.8.0.12")));

        WorkspaceFileRouteResponse response = service(workspaceRepository, assignmentService, heartbeatStore)
                .routeWorkspace(USER_ID, "opencode", WORKSPACE_ID, TRACE_ID);

        assertThat(response.linuxServerId()).isEqualTo("10.8.0.12");
        assertThat(response.baseUrl()).isEqualTo("http://10.8.0.12:8080");
        assertThat(response.webSocketPath()).isEqualTo(WorkspaceFileRoutingService.WEB_SOCKET_PATH);
    }

    @Test
    void routesWorkspaceFilesWithoutCallingBlockingProcessStatus() {
        WorkspaceRepository workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        OpencodeProcessHeartbeatStore heartbeatStore = Mockito.mock(OpencodeProcessHeartbeatStore.class);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace("10.8.0.12")));
        when(assignmentService.status(USER_ID, "opencode", TRACE_ID))
                .thenThrow(new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "opencode 管理进程命令超时"));
        when(assignmentService.fileRoutingAffinity(USER_ID, "opencode", TRACE_ID)).thenReturn(affinity("10.8.0.12"));
        when(heartbeatStore.liveBackendSnapshots()).thenReturn(List.of(backendSnapshot("10.8.0.12")));

        WorkspaceFileRouteResponse response = service(workspaceRepository, assignmentService, heartbeatStore)
                .routeWorkspace(USER_ID, "opencode", WORKSPACE_ID, TRACE_ID);

        assertThat(response.baseUrl()).isEqualTo("http://10.8.0.12:8080");
        Mockito.verify(assignmentService, Mockito.never()).status(USER_ID, "opencode", TRACE_ID);
    }

    @Test
    void rejectsWorkspaceFilesWhenWorkspaceAndAgentAreOnDifferentLinuxServers() {
        WorkspaceRepository workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        OpencodeProcessHeartbeatStore heartbeatStore = Mockito.mock(OpencodeProcessHeartbeatStore.class);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace("10.8.0.12")));
        when(assignmentService.fileRoutingAffinity(eq(USER_ID), eq("opencode"), eq(TRACE_ID))).thenReturn(affinity("10.8.0.13"));

        assertThatThrownBy(() -> service(workspaceRepository, assignmentService, heartbeatStore)
                        .routeWorkspace(USER_ID, "opencode", WORKSPACE_ID, TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void rebindsWorkspaceWhenStoredLinuxServerIsStaleAndCurrentPathExists() {
        WorkspaceRepository workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        OpencodeProcessHeartbeatStore heartbeatStore = Mockito.mock(OpencodeProcessHeartbeatStore.class);
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        ConversationContextWorkspaceMutation mutation =
                new ConversationContextWorkspaceMutation(WORKSPACE_ID, "mutation-file-route");
        when(contextStore.beginWorkspaceMutation(WORKSPACE_ID)).thenReturn(mutation);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace("10.8.0.99", root.toString())));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentService.fileRoutingAffinity(USER_ID, "opencode", TRACE_ID)).thenReturn(affinity("10.8.0.12"));
        when(heartbeatStore.liveBackendSnapshots()).thenReturn(List.of(backendSnapshot("10.8.0.12")));

        WorkspaceFileRouteResponse response = service(workspaceRepository, assignmentService, heartbeatStore, contextStore)
                .routeWorkspace(USER_ID, "opencode", WORKSPACE_ID, TRACE_ID);

        assertThat(response.linuxServerId()).isEqualTo("10.8.0.12");
        verify(workspaceRepository).save(Mockito.argThat(workspace ->
                "10.8.0.12".equals(workspace.linuxServerId()) && WORKSPACE_ID.equals(workspace.workspaceId())));
        verify(contextStore).beginWorkspaceMutation(WORKSPACE_ID);
        verify(contextStore).completeWorkspaceMutation(mutation);
    }

    @Test
    void keepsConflictWhenStoredLinuxServerStillHasReadyBackend() {
        WorkspaceRepository workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        OpencodeProcessHeartbeatStore heartbeatStore = Mockito.mock(OpencodeProcessHeartbeatStore.class);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace("10.8.0.99", root.toString())));
        when(assignmentService.fileRoutingAffinity(USER_ID, "opencode", TRACE_ID)).thenReturn(affinity("10.8.0.12"));
        when(heartbeatStore.liveBackendSnapshots()).thenReturn(List.of(backendSnapshot("10.8.0.12"), backendSnapshot("10.8.0.99")));

        assertThatThrownBy(() -> service(workspaceRepository, assignmentService, heartbeatStore)
                        .routeWorkspace(USER_ID, "opencode", WORKSPACE_ID, TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(workspaceRepository, never()).save(any(Workspace.class));
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

    private static WorkspaceFileRoutingService service(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ConversationContextStore contextStore) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ManagerControlSettings settings = new ManagerControlSettings(
                "secret-token",
                "http://10.8.0.12:8080",
                new LinuxServerId("10.8.0.12"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                100);
        return new WorkspaceFileRoutingService(
                workspaceRepository,
                assignmentService,
                new BackendJavaRouteResolver(heartbeatStore, settings, clock),
                ManagedWorkspacePathResolver.legacyOnly(),
                clock,
                contextStore);
    }

    private static Workspace workspace(String linuxServerId) {
        return workspace(linuxServerId, "/tmp/demo");
    }

    private static Workspace workspace(String linuxServerId, String rootPath) {
        return new Workspace(
                WORKSPACE_ID,
                "Demo",
                rootPath,
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

    private static UserOpencodeProcessFileRoutingAffinity affinity(String linuxServerId) {
        return new UserOpencodeProcessFileRoutingAffinity(
                UserOpencodeProcessAvailability.READY,
                false,
                "ready",
                "ocp_1234567890abcdef",
                linuxServerId,
                "ctr_01",
                4096,
                linuxServerId + ":4096",
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
