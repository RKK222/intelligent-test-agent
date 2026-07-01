package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessFileRoutingAffinity;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.icbc.testagent.workspace.WorkspaceApplicationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkspaceFileSocketTicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");

    @Test
    void createsWorkspaceTicketFromReadyAffinityWithoutCallingBlockingProcessStatus() {
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        WorkspaceFileSocketTicketService service = service(workspaceService, assignmentService);
        when(workspaceService.currentLinuxServerId()).thenReturn("10.8.0.12");
        when(assignmentService.status(USER_ID, "opencode", TRACE_ID))
                .thenThrow(new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "opencode 管理进程命令超时"));
        when(assignmentService.fileRoutingAffinity(USER_ID, "opencode", TRACE_ID)).thenReturn(readyAffinity("10.8.0.12"));

        WorkspaceFileSocketDtos.TicketResponse response = service.createTicket(
                principal(List.of(Dictionary.ROLE_USER)),
                new WorkspaceFileSocketDtos.TicketRequest("wrk_1234567890abcdef", "10.8.0.12", "workspace"),
                TRACE_ID);

        assertThat(response.ticket()).isEqualTo("wft_fixed");
        assertThat(response.webSocketUrl()).isEqualTo("/api/internal/platform/workspace-management/file/ws?ticket=wft_fixed");
        verify(assignmentService, never()).status(USER_ID, "opencode", TRACE_ID);
        verify(workspaceService).requireWorkspaceOnCurrentServer(new WorkspaceId("wrk_1234567890abcdef"), TRACE_ID);
    }

    @Test
    void rejectsWorkspaceTicketWhenTargetBackendDoesNotMatchCurrentBackend() {
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        WorkspaceFileSocketTicketService service = service(workspaceService, assignmentService);
        when(workspaceService.currentLinuxServerId()).thenReturn("10.8.0.12");

        assertThatThrownBy(() -> service.createTicket(
                        principal(List.of(Dictionary.ROLE_USER)),
                        new WorkspaceFileSocketDtos.TicketRequest("wrk_1234567890abcdef", "10.8.0.13", "workspace"),
                        TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(assignmentService, never()).fileRoutingAffinity(USER_ID, "opencode", TRACE_ID);
    }

    @Test
    void createsWorkspaceTicketWhenAffinityIsStaleButStrongStatusIsReady() {
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        WorkspaceFileSocketTicketService service = service(workspaceService, assignmentService);
        when(workspaceService.currentLinuxServerId()).thenReturn("10.8.0.12");
        when(assignmentService.fileRoutingAffinity(USER_ID, "opencode", TRACE_ID)).thenReturn(needsInitializationAffinity());
        when(assignmentService.status(USER_ID, "opencode", TRACE_ID)).thenReturn(readyStatus("10.8.0.12"));

        WorkspaceFileSocketDtos.TicketResponse response = service.createTicket(
                principal(List.of(Dictionary.ROLE_USER)),
                new WorkspaceFileSocketDtos.TicketRequest("wrk_1234567890abcdef", "10.8.0.12", "workspace"),
                TRACE_ID);

        assertThat(response.ticket()).isEqualTo("wft_fixed");
        verify(assignmentService).status(USER_ID, "opencode", TRACE_ID);
        verify(workspaceService).requireWorkspaceOnCurrentServer(new WorkspaceId("wrk_1234567890abcdef"), TRACE_ID);
    }

    @Test
    void rejectsWorkspaceTicketWhenAffinityAndStrongStatusAreNotReady() {
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        WorkspaceFileSocketTicketService service = service(workspaceService, assignmentService);
        when(workspaceService.currentLinuxServerId()).thenReturn("10.8.0.12");
        when(assignmentService.fileRoutingAffinity(USER_ID, "opencode", TRACE_ID)).thenReturn(needsInitializationAffinity());
        when(assignmentService.status(USER_ID, "opencode", TRACE_ID)).thenReturn(needsInitializationStatus());

        assertThatThrownBy(() -> service.createTicket(
                        principal(List.of(Dictionary.ROLE_USER)),
                        new WorkspaceFileSocketDtos.TicketRequest("wrk_1234567890abcdef", "10.8.0.12", "workspace"),
                        TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));
    }

    @Test
    void createsAgentConfigTicketWithoutUserOpencodeProcessAffinity() {
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        WorkspaceFileSocketTicketService service = service(workspaceService, assignmentService);
        when(workspaceService.currentLinuxServerId()).thenReturn("10.8.0.12");

        WorkspaceFileSocketDtos.TicketResponse response = service.createTicket(
                principal(List.of(Dictionary.ROLE_USER)),
                new WorkspaceFileSocketDtos.TicketRequest(
                        null,
                        "10.8.0.12",
                        "agent-config",
                        "PUBLIC",
                        "agw_1234567890abcdef"),
                TRACE_ID);

        assertThat(response.ticket()).isEqualTo("wft_fixed");
        assertThat(response.webSocketUrl()).isEqualTo("/api/internal/platform/workspace-management/file/ws?ticket=wft_fixed");
        WorkspaceFileSocketTicket ticket = service.consume("wft_fixed", "http://localhost:3000");
        assertThat(ticket.mode()).isEqualTo("agent-config");
        assertThat(ticket.scope()).isEqualTo("PUBLIC");
        assertThat(ticket.worktreeId()).isEqualTo("agw_1234567890abcdef");
        verify(assignmentService, never()).fileRoutingAffinity(USER_ID, "opencode", TRACE_ID);
    }

    @Test
    void rejectsAgentConfigTicketWhenTargetBackendDoesNotMatchCurrentBackend() {
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        WorkspaceFileSocketTicketService service = service(workspaceService, assignmentService);
        when(workspaceService.currentLinuxServerId()).thenReturn("10.8.0.12");

        assertThatThrownBy(() -> service.createTicket(
                        principal(List.of(Dictionary.ROLE_SUPER_ADMIN)),
                        new WorkspaceFileSocketDtos.TicketRequest(
                                null,
                                "10.8.0.13",
                                "agent-config",
                                "PUBLIC",
                                "agw_1234567890abcdef"),
                        TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(assignmentService, never()).fileRoutingAffinity(USER_ID, "opencode", TRACE_ID);
    }

    private static WorkspaceFileSocketTicketService service(
            WorkspaceApplicationService workspaceService,
            UserOpencodeProcessAssignmentService assignmentService) {
        return new WorkspaceFileSocketTicketService(
                workspaceService,
                assignmentService,
                new WorkspaceFileSocketTicketStore(Clock.fixed(NOW, ZoneOffset.UTC), () -> "wft_fixed"));
    }

    private static UserOpencodeProcessFileRoutingAffinity readyAffinity(String linuxServerId) {
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

    private static UserOpencodeProcessFileRoutingAffinity needsInitializationAffinity() {
        return new UserOpencodeProcessFileRoutingAffinity(
                UserOpencodeProcessAvailability.NEEDS_INITIALIZATION,
                true,
                "需要初始化 opencode 进程",
                null,
                null,
                null,
                null,
                null,
                NOW);
    }

    private static UserOpencodeProcessStatusResponse readyStatus(String linuxServerId) {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.READY,
                false,
                "opencode 进程可用",
                "ocp_1234567890abcdef",
                linuxServerId,
                "ctr_01",
                4096,
                "http://" + linuxServerId + ":4096",
                NOW);
    }

    private static UserOpencodeProcessStatusResponse needsInitializationStatus() {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.NEEDS_INITIALIZATION,
                true,
                "需要初始化 opencode 进程",
                null,
                null,
                null,
                null,
                null,
                NOW);
    }

    private static AuthPrincipal principal(List<String> roles) {
        return new AuthPrincipal(
                "token_123",
                USER_ID,
                "tester",
                "tester",
                roles,
                NOW,
                NOW.plusSeconds(3600));
    }
}
