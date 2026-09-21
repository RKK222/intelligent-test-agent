package com.enterprise.testagent.system.management.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.team.SystemAdminTeamMember;
import com.enterprise.testagent.domain.team.SystemAdminTeamRepository;
import com.enterprise.testagent.domain.team.TeamOversightAuditRepository;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserManagementQueryRepository;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.system.management.auth.LiveRoleCapabilityService;
import com.enterprise.testagent.system.management.auth.LiveRoleCapabilityService.LiveActor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证团队范围、超级管理员视图和移除成员后的实时撤权。 */
class SystemAdminTeamApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T08:00:00Z");
    private static final UserId OWNER_ID = new UserId("system-owner");
    private static final UserId MEMBER_ID = new UserId("member-user");
    private final SystemAdminTeamRepository teams = mock(SystemAdminTeamRepository.class);
    private final TeamOversightAuditRepository audits = mock(TeamOversightAuditRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserManagementQueryRepository userQueries = mock(UserManagementQueryRepository.class);
    private final LiveRoleCapabilityService liveRoles = mock(LiveRoleCapabilityService.class);
    private SystemAdminTeamApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SystemAdminTeamApplicationService(
                teams, audits, users, userQueries, liveRoles,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void systemAdminAddsOwnMemberAndRemovalRevokesNextAuthorization() {
        User owner = user(OWNER_ID, "system-admin");
        User member = user(MEMBER_ID, "member");
        AuthPrincipal principal = principal(owner, Dictionary.ROLE_SYSTEM_ADMIN);
        LiveActor actor = new LiveActor(owner, List.of(Dictionary.ROLE_SYSTEM_ADMIN));
        when(liveRoles.require(principal, Dictionary.ROLE_SYSTEM_ADMIN)).thenReturn(actor);
        when(liveRoles.require(OWNER_ID, Dictionary.ROLE_SYSTEM_ADMIN)).thenReturn(actor);
        when(users.findByUserId(MEMBER_ID)).thenReturn(Optional.of(member));
        when(teams.find(OWNER_ID, MEMBER_ID)).thenReturn(Optional.empty());
        when(teams.isActiveMember(OWNER_ID, MEMBER_ID)).thenReturn(true, false);

        service.addMember(principal, TeamScopeMode.MY_TEAM, null, MEMBER_ID.value(), context());
        ArgumentCaptor<SystemAdminTeamMember> saved = ArgumentCaptor.forClass(SystemAdminTeamMember.class);
        verify(teams).save(saved.capture());
        assertThat(saved.getValue().ownerUserId()).isEqualTo(OWNER_ID);
        assertThat(saved.getValue().memberUserId()).isEqualTo(MEMBER_ID);
        assertThat(service.authorizeTarget(OWNER_ID, TeamScopeMode.MY_TEAM, null, MEMBER_ID).target())
                .isEqualTo(member);
        assertThatThrownBy(() -> service.authorizeTarget(
                OWNER_ID, TeamScopeMode.MY_TEAM, null, MEMBER_ID))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void superAdminCanUseGlobalOrAnActualSystemAdminTeam() {
        User superAdmin = user(new UserId("super-user"), "super-admin");
        User owner = user(OWNER_ID, "system-admin");
        AuthPrincipal principal = principal(superAdmin, Dictionary.ROLE_SUPER_ADMIN);
        when(liveRoles.require(principal, Dictionary.ROLE_SUPER_ADMIN))
                .thenReturn(new LiveActor(superAdmin, List.of(Dictionary.ROLE_SUPER_ADMIN)));
        when(liveRoles.require(OWNER_ID, Dictionary.ROLE_SYSTEM_ADMIN))
                .thenReturn(new LiveActor(owner, List.of(Dictionary.ROLE_SYSTEM_ADMIN)));

        assertThat(service.authorizeScope(principal, TeamScopeMode.GLOBAL, null).scope().global()).isTrue();
        assertThat(service.authorizeScope(
                principal, TeamScopeMode.SYSTEM_ADMIN_TEAM, OWNER_ID.value()).scope().ownerUserId())
                .isEqualTo(OWNER_ID.value());

        when(liveRoles.require(OWNER_ID, Dictionary.ROLE_SYSTEM_ADMIN))
                .thenReturn(new LiveActor(owner, List.of(Dictionary.ROLE_SUPER_ADMIN)));
        assertThatThrownBy(() -> service.authorizeScope(
                principal, TeamScopeMode.SYSTEM_ADMIN_TEAM, OWNER_ID.value()))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private AuthPrincipal principal(User user, String role) {
        return new AuthPrincipal(
                "token", user.userId(), user.username(), user.unifiedAuthId(), List.of(role),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
    }

    private User user(UserId id, String name) {
        return new User(
                id, "auth-" + name, name, "hash", "org", "rd", "department",
                UserStatus.ACTIVE, NOW.minusSeconds(3600), NOW);
    }

    private TeamOversightRequestContext context() {
        return new TeamOversightRequestContext("trace-team", "127.0.0.1", "test-agent");
    }
}
