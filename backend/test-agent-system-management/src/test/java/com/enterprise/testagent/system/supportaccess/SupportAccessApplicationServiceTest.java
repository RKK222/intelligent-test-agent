package com.enterprise.testagent.system.supportaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.dictionary.DictId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRole;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.supportaccess.SupportAccessGrant;
import com.enterprise.testagent.domain.supportaccess.SupportAccessGrantSession;
import com.enterprise.testagent.domain.supportaccess.SupportAccessGrantStore;
import com.enterprise.testagent.domain.supportaccess.SupportAccessRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 排查授权签发、实时失效和审计 fail-closed 规则。 */
class SupportAccessApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T05:00:00Z");
    private static final UserId ACTOR_ID = new UserId("usr_support_actor");
    private static final UserId TARGET_ID = new UserId("usr_support_target");
    private static final String SESSION_DIGEST = "1".repeat(64);
    private static final String RAW_GRANT_TOKEN = "sat_" + "a".repeat(43);
    private static final String GRANT_DIGEST = TokenSessionMarkerStore.sha256(RAW_GRANT_TOKEN);

    private SupportAccessRepository repository;
    private SupportAccessGrantStore grantStore;
    private TokenSessionMarkerStore markerStore;
    private UserRepository userRepository;
    private UserRoleRepository userRoleRepository;
    private DictionaryRepository dictionaryRepository;
    private SupportAccessApplicationService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SupportAccessRepository.class);
        grantStore = Mockito.mock(SupportAccessGrantStore.class);
        markerStore = Mockito.mock(TokenSessionMarkerStore.class);
        userRepository = Mockito.mock(UserRepository.class);
        userRoleRepository = Mockito.mock(UserRoleRepository.class);
        dictionaryRepository = Mockito.mock(DictionaryRepository.class);
        service = new SupportAccessApplicationService(
                repository,
                grantStore,
                markerStore,
                userRepository,
                userRoleRepository,
                dictionaryRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> RAW_GRANT_TOKEN);
        when(userRepository.findByUserId(ACTOR_ID)).thenReturn(Optional.of(user(ACTOR_ID, "support-admin")));
        when(userRepository.findByUserId(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, "target-user")));
        DictId roleId = new DictId("dict_support_super_admin");
        when(userRoleRepository.findByUserId(ACTOR_ID)).thenReturn(List.of(new UserRole(ACTOR_ID, roleId, NOW)));
        when(dictionaryRepository.findByDictId(roleId)).thenReturn(Optional.of(new Dictionary(
                roleId, "应用角色", Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_SUPER_ADMIN, "超级管理员", 1, NOW, NOW)));
        when(markerStore.digest("platform-token")).thenReturn(SESSION_DIGEST);
        when(markerStore.isActiveForUser(SESSION_DIGEST, ACTOR_ID)).thenReturn(true);
        when(repository.saveGrant(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(grantStore.rotate(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void issueBindsGrantToCurrentPlatformSessionAndAuditsWithoutSharedPhrase() {
        SupportAccessGrantIssue issue = service.issue(
                principal(), "INC-2026-001", "定位会话恢复异常", 30, true, requestContext());

        assertThat(issue.grantToken()).isEqualTo(RAW_GRANT_TOKEN);
        assertThat(issue.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        ArgumentCaptor<SupportAccessGrant> grantCaptor = ArgumentCaptor.forClass(SupportAccessGrant.class);
        verify(repository).saveGrant(grantCaptor.capture());
        assertThat(grantCaptor.getValue().sessionDigest()).isEqualTo(SESSION_DIGEST);
        assertThat(grantCaptor.getValue().incidentId()).isEqualTo("INC-2026-001");
        verify(repository).appendAuditEvent(any());
    }

    @Test
    void returnsLatestIncidentOnlyForCurrentLiveSuperAdmin() {
        when(repository.findLatestIncidentId(ACTOR_ID)).thenReturn(Optional.of("INC-PERSISTED"));

        assertThat(service.findLatestIncidentId(principal())).contains("INC-PERSISTED");

        verify(repository).findLatestIncidentId(ACTOR_ID);
    }

    @Test
    void authorizationFailsImmediatelyAfterLiveRoleIsRemoved() {
        SupportAccessGrant grant = grant();
        SupportAccessGrantSession session = session(grant);
        when(grantStore.findByTokenDigest(GRANT_DIGEST)).thenReturn(Optional.of(session));
        when(repository.findGrant(grant.grantId())).thenReturn(Optional.of(grant));
        when(userRoleRepository.findByUserId(ACTOR_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.authorize(
                        principal(), RAW_GRANT_TOKEN, TARGET_ID, "SESSION_LIST", "USER", TARGET_ID.value(),
                        null, requestContext(), true))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(repository, never()).appendAuditEvent(any());
    }

    @Test
    void successfulReadIsReturnedOnlyAfterAuditAppend() {
        SupportAccessGrant grant = grant();
        SupportAccessGrantSession session = session(grant);
        when(grantStore.findByTokenDigest(GRANT_DIGEST)).thenReturn(Optional.of(session));
        when(repository.findGrant(grant.grantId())).thenReturn(Optional.of(grant));
        SupportAccessAuthorization authorization = service.authorize(
                principal(), RAW_GRANT_TOKEN, TARGET_ID, "SESSION_LIST", "USER", TARGET_ID.value(),
                null, requestContext(), true);

        String result = service.executeRead(
                authorization, "SESSION_LIST", "USER", TARGET_ID.value(), null, requestContext(), () -> "content");

        assertThat(result).isEqualTo("content");
        verify(repository).appendAuditEvent(any());
    }

    @Test
    void auditFailurePreventsReadResultFromBeingDelivered() {
        SupportAccessGrant grant = grant();
        SupportAccessGrantSession session = session(grant);
        when(grantStore.findByTokenDigest(GRANT_DIGEST)).thenReturn(Optional.of(session));
        when(repository.findGrant(grant.grantId())).thenReturn(Optional.of(grant));
        Mockito.doThrow(new IllegalStateException("audit unavailable"))
                .when(repository).appendAuditEvent(any());
        SupportAccessAuthorization authorization = service.authorize(
                principal(), RAW_GRANT_TOKEN, TARGET_ID, "SESSION_LIST", "USER", TARGET_ID.value(),
                null, requestContext(), true);

        assertThatThrownBy(() -> service.executeRead(
                        authorization, "SESSION_LIST", "USER", TARGET_ID.value(), null, requestContext(), () -> "content"))
                .isInstanceOf(IllegalStateException.class);
    }

    private SupportAccessGrant grant() {
        return new SupportAccessGrant(
                "sag_support_test", ACTOR_ID, "support-admin", "INC-2026-001", "定位异常", SESSION_DIGEST,
                NOW, NOW.plusSeconds(1800), null, null, "trace_support_test");
    }

    private SupportAccessGrantSession session(SupportAccessGrant grant) {
        return new SupportAccessGrantSession(
                grant.grantId(), ACTOR_ID.value(), SESSION_DIGEST, GRANT_DIGEST, grant.expiresAt());
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(
                "platform-token", ACTOR_ID, "support-admin", "auth-support-admin",
                List.of(Dictionary.ROLE_SUPER_ADMIN), NOW, Instant.parse("2031-01-01T00:00:00Z"));
    }

    private SupportAccessRequestContext requestContext() {
        return new SupportAccessRequestContext("trace_support_test", "127.0.0.1", "test-agent");
    }

    private User user(UserId userId, String username) {
        return new User(
                userId, "auth-" + username, username, "hash", null, null, null,
                UserStatus.ACTIVE, NOW.minusSeconds(3600), NOW);
    }
}
