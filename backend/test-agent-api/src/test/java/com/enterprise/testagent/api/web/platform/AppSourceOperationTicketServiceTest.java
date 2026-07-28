package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppSourceOperationTicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T04:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1");

    @Test
    void ticketIsIssuedOnlyAfterCurrentOperationAuthorizationAndPinsTheIssuerJvmUrl() {
        AppSourceApplicationService appSources = mock(AppSourceApplicationService.class);
        when(appSources.getOperation("aso_12345678", USER_ID, true)).thenReturn(operation());
        AppSourceOperationTicketStore store = new AppSourceOperationTicketStore(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "ast_ticket_1");
        AppSourceOperationTicketService service = new AppSourceOperationTicketService(
                store, appSources, new CurrentBackendWebSocketUrlFactory(identity()), identity());
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "U001", "管理员", List.of("APP_ADMIN"),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));

        AppSourceDtos.TicketResponse response =
                service.createTicket(
                        principal, "aso_12345678", "https://Console.Example:443", "trace_ticket");

        assertThat(response.ticket()).isEqualTo("ast_ticket_1");
        assertThat(response.webSocketUrl()).isEqualTo(
                "ws://server-a:8080/api/internal/platform/workspace-management/"
                        + "app-source-operations/aso_12345678/ws?ticket=ast_ticket_1");
        assertThat(service.consume("ast_ticket_1", "aso_12345678", "https://console.example").userId())
                .isEqualTo(USER_ID.value());
        verify(appSources).getOperation("aso_12345678", USER_ID, true);
    }

    @Test
    void materializationAcceptedOperationIdCanAlwaysBeUsedToCreateATicket() {
        AppSourceApplicationService.MaterializationCommand command =
                new AppSourceApplicationService.MaterializationCommand(
                        "release..1", null, "main", "b".repeat(40), List.of(),
                        AppSourcePurpose.TEAM, 1, false);
        AppSourceApplicationService appSources = mock(AppSourceApplicationService.class);
        when(appSources.getOperation("release..1", USER_ID, false)).thenReturn(operation());
        AppSourceOperationTicketService service = new AppSourceOperationTicketService(
                new AppSourceOperationTicketStore(
                        Clock.fixed(NOW, ZoneOffset.UTC), () -> "ast_ticket_job"),
                appSources,
                new CurrentBackendWebSocketUrlFactory(identity()),
                identity());
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "U001", "普通用户", List.of("USER"),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));

        assertThatCode(() -> service.createTicket(
                        principal, command.operationId(), "https://console.example", "trace_ticket"))
                .doesNotThrowAnyException();
    }

    @Test
    void missingOriginCannotCreateATicket() {
        AppSourceApplicationService appSources = mock(AppSourceApplicationService.class);
        when(appSources.getOperation("job_123", USER_ID, false)).thenReturn(operation());
        AppSourceOperationTicketService service = new AppSourceOperationTicketService(
                new AppSourceOperationTicketStore(
                        Clock.fixed(NOW, ZoneOffset.UTC), () -> "ast_ticket_missing_origin"),
                appSources,
                new CurrentBackendWebSocketUrlFactory(identity()),
                identity());
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "U001", "普通用户", List.of("USER"),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.createTicket(principal, "job_123", null, "trace_ticket"))
                .isInstanceOfSatisfying(
                        com.enterprise.testagent.common.error.PlatformException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(com.enterprise.testagent.common.error.ErrorCode.FORBIDDEN));
    }

    private AppSourceApplicationService.OperationSnapshot operation() {
        return new AppSourceApplicationService.OperationSnapshot(
                "aso_12345678", "app_1", "repo_1", null, 1L,
                AppSourceOperationType.DOWNLOAD, AppSourceOperationStatus.RUNNING,
                AppSourcePurpose.TEAM, "main", "b".repeat(40), List.of(),
                NOW.plusSeconds(3600), "trace_operation", NOW, null, List.of(), List.of());
    }

    private BackendInstanceIdentity identity() {
        return new BackendInstanceIdentity() {
            @Override
            public String instanceId() {
                return "instance-a";
            }

            @Override
            public String linuxServerId() {
                return "server-a";
            }

            @Override
            public String backendProcessId() {
                return "bjp_server_a";
            }

            @Override
            public String listenUrl() {
                return "http://server-a:8080";
            }
        };
    }
}
