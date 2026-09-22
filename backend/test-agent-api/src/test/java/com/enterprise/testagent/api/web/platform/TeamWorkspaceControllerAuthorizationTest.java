package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.team.TeamOversightAuditEvent;
import com.enterprise.testagent.domain.team.TeamOversightAuditRepository;
import com.enterprise.testagent.domain.team.SystemAdminTeamRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserManagementQueryRepository;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import com.enterprise.testagent.system.management.auth.LiveRoleCapabilityService;
import com.enterprise.testagent.system.management.auth.LiveRoleCapabilityService.LiveActor;
import com.enterprise.testagent.system.management.team.SystemAdminTeamApplicationService;
import com.enterprise.testagent.workspace.TeamWorkspaceApplicationService;
import com.enterprise.testagent.workspace.TeamWorkspaceExportService;
import com.enterprise.testagent.workspace.TeamWorkspaceResponses.ApplicationResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 成员上下文查询必须实时校验团队归属，并只把该成员交给列表服务。 */
class TeamWorkspaceControllerAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-09-22T03:00:00Z");
    private static final UserId OWNER_ID = new UserId("system-owner");
    private static final UserId MEMBER_ID = new UserId("member-user");
    private static final String TRACE_ID = "trace_team_member_context";

    private final SystemAdminTeamRepository teams = mock(SystemAdminTeamRepository.class);
    private final TeamOversightAuditRepository audits = mock(TeamOversightAuditRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserManagementQueryRepository userQueries = mock(UserManagementQueryRepository.class);
    private final LiveRoleCapabilityService liveRoles = mock(LiveRoleCapabilityService.class);
    private final TeamWorkspaceApplicationService workspaces = mock(TeamWorkspaceApplicationService.class);
    private SystemAdminTeamApplicationService teamService;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        teamService = new SystemAdminTeamApplicationService(
                teams, audits, users, userQueries, liveRoles, Clock.fixed(NOW, ZoneOffset.UTC));
        User owner = user(OWNER_ID, "system-admin");
        AuthPrincipal principal = new AuthPrincipal(
                "token", OWNER_ID, owner.username(), owner.unifiedAuthId(),
                List.of(Dictionary.ROLE_SYSTEM_ADMIN), NOW.minusSeconds(60), NOW.plusSeconds(3600));
        when(liveRoles.require(principal, Dictionary.ROLE_SYSTEM_ADMIN))
                .thenReturn(new LiveActor(owner, List.of(Dictionary.ROLE_SYSTEM_ADMIN)));
        when(liveRoles.require(OWNER_ID, Dictionary.ROLE_SYSTEM_ADMIN))
                .thenReturn(new LiveActor(owner, List.of(Dictionary.ROLE_SYSTEM_ADMIN)));
        client = WebTestClient.bindToController(new TeamWorkspaceController(
                        teamService,
                        workspaces,
                        mock(BackendJavaRouteResolver.class),
                        mock(BackendHttpForwarder.class),
                        mock(WorkspaceFileRoutingService.class),
                        mock(WorkspaceFileSocketTicketService.class),
                        mock(TeamWorkspaceExportService.class),
                        mock(TeamWorkspaceExportDownloadTicketStore.class)))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void memberContextRequiresActiveTeamMembershipAndAuditsTheTarget() {
        User member = user(MEMBER_ID, "member");
        when(users.findByUserId(MEMBER_ID)).thenReturn(Optional.of(member));
        when(teams.isActiveMember(OWNER_ID, MEMBER_ID)).thenReturn(true);
        when(workspaces.applications(false, OWNER_ID, MEMBER_ID)).thenReturn(List.of(
                new ApplicationResponse("app-1", "历史应用", true, 0, 1, "HISTORICAL")));

        client.get()
                .uri("/api/internal/platform/workspace-management/team/applications?scopeMode=MY_TEAM&targetUserId=member-user")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].appId").isEqualTo("app-1")
                .jsonPath("$.data[0].membershipState").isEqualTo("HISTORICAL");

        ArgumentCaptor<TeamOversightAuditEvent> event = ArgumentCaptor.forClass(TeamOversightAuditEvent.class);
        verify(audits).append(event.capture());
        assertThat(event.getValue().action()).isEqualTo("APPLICATION_LIST");
        assertThat(event.getValue().targetUserId()).isEqualTo(MEMBER_ID.value());
        assertThat(event.getValue().outcome()).isEqualTo("SUCCESS");
    }

    @Test
    void omittedTargetKeepsTheTeamWideCatalog() {
        when(workspaces.applications(false, OWNER_ID, null)).thenReturn(List.of(
                new ApplicationResponse("app-1", "团队应用", true, 2, 0, "CURRENT")));

        client.get()
                .uri("/api/internal/platform/workspace-management/team/applications?scopeMode=MY_TEAM")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].membershipState").isEqualTo("CURRENT");

        verify(users, never()).findByUserId(MEMBER_ID);
        ArgumentCaptor<TeamOversightAuditEvent> event = ArgumentCaptor.forClass(TeamOversightAuditEvent.class);
        verify(audits).append(event.capture());
        assertThat(event.getValue().targetUserId()).isNull();
    }

    @Test
    void removedMemberCannotBeUsedAsReviewContext() {
        User member = user(MEMBER_ID, "member");
        when(users.findByUserId(MEMBER_ID)).thenReturn(Optional.of(member));
        when(teams.isActiveMember(OWNER_ID, MEMBER_ID)).thenReturn(false);

        client.get()
                .uri("/api/internal/platform/workspace-management/team/applications?scopeMode=MY_TEAM&targetUserId=member-user")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden();

        verify(workspaces, never()).applications(false, OWNER_ID, MEMBER_ID);
    }

    private User user(UserId id, String name) {
        return new User(
                id, "auth-" + name, name, "hash", "org", "rd", "department",
                UserStatus.ACTIVE, NOW.minusSeconds(3600), NOW);
    }
}
