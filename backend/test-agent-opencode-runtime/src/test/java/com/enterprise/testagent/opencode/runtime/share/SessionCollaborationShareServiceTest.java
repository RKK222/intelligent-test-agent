package com.enterprise.testagent.opencode.runtime.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.sessionshare.SessionShare;
import com.enterprise.testagent.domain.sessionshare.SessionShareAuditEvent;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.sessionshare.SessionShareRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** 会话分享管理与代操作上下文测试。 */
class SessionCollaborationShareServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");
    private static final UserId OWNER = new UserId("usr_share_owner");
    private static final UserId MEMBER = new UserId("usr_share_member");
    private static final UserId READ_ONLY = new UserId("usr_share_read_only");
    private static final SessionId SESSION = new SessionId("ses_share_service");
    private static final WorkspaceId WORKSPACE = new WorkspaceId("wrk_share_service");

    @Mock
    private SessionShareRepository shareRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SecureRandom secureRandom;

    private SessionCollaborationShareService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SessionCollaborationShareService(
                shareRepository,
                sessionRepository,
                userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                secureRandom);
        when(sessionRepository.findById(SESSION)).thenReturn(Optional.of(session()));
        when(userRepository.findByUserId(OWNER)).thenReturn(Optional.of(user(OWNER, "ucid-owner", "所属人", UserStatus.ACTIVE)));
        when(userRepository.findByUserId(MEMBER)).thenReturn(Optional.of(user(MEMBER, "ucid-member", "协作人", UserStatus.ACTIVE)));
        when(userRepository.findByUserId(READ_ONLY)).thenReturn(Optional.of(user(READ_ONLY, "ucid-read", "只读人", UserStatus.ACTIVE)));
        doAnswer(invocation -> {
            byte[] target = invocation.getArgument(0);
            for (int index = 0; index < target.length; index++) {
                target[index] = (byte) index;
            }
            return null;
        }).when(secureRandom).nextBytes(any(byte[].class));
    }

    @Test
    void createsAndUpdatesOneStableLinkWithOptimisticVersion() {
        when(shareRepository.findBySessionId(SESSION)).thenReturn(Optional.empty());
        when(shareRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SessionShare created = service.put(
                OWNER, SESSION, null, NOW.plus(Duration.ofDays(3)),
                List.of(new SessionShareMemberCommand(MEMBER, true)), "trace_share_create");

        assertThat(created.shareId().value()).isEqualTo(
                "shr_000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        when(shareRepository.findBySessionId(SESSION)).thenReturn(Optional.of(created));
        when(shareRepository.update(any(), eq(0L))).thenReturn(true);

        SessionShare updated = service.put(
                OWNER, SESSION, 0L, NOW.plus(Duration.ofDays(5)),
                List.of(new SessionShareMemberCommand(MEMBER, false)), "trace_share_update");

        assertThat(updated.shareId()).isEqualTo(created.shareId());
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.membership(MEMBER)).get().satisfies(member -> assertThat(member.canChat()).isFalse());
    }

    @Test
    void rejectsInactiveMembersAndStaleExpectedVersion() {
        UserId inactive = new UserId("usr_share_inactive");
        when(userRepository.findByUserId(inactive))
                .thenReturn(Optional.of(user(inactive, "ucid-inactive", "停用人", UserStatus.INACTIVE)));
        when(shareRepository.findBySessionId(SESSION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.put(
                OWNER, SESSION, null, NOW.plus(Duration.ofDays(1)),
                List.of(new SessionShareMemberCommand(inactive, true)), "trace_share_inactive"))
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        SessionShare existing = share(true, NOW.plus(Duration.ofDays(1)));
        when(shareRepository.findBySessionId(SESSION)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.put(
                OWNER, SESSION, 9L, NOW.plus(Duration.ofDays(1)),
                List.of(new SessionShareMemberCommand(MEMBER, true)), "trace_share_stale"))
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.SESSION_SHARE_VERSION_CONFLICT);
    }

    @Test
    void resolvesDelegatedContextWithoutReplacingActorAndEnforcesCanChat() {
        SessionShare share = share(true, NOW.plus(Duration.ofDays(1)));
        when(shareRepository.findByShareId(share.shareId())).thenReturn(Optional.of(share));

        DelegatedOperationContext context = service.requireAccess(MEMBER, share.shareId(), false, "trace_share_access");

        assertThat(context.actorUserId()).isEqualTo(MEMBER);
        assertThat(context.executionOwnerUserId()).isEqualTo(OWNER);
        assertThat(context.sessionId()).isEqualTo(SESSION);
        assertThat(context.workspaceId()).isEqualTo(WORKSPACE);
        assertThat(context.delegated()).isTrue();
        assertThat(context.canChat()).isTrue();

        SessionShare readOnlyShare = share(false, NOW.plus(Duration.ofDays(1)));
        when(shareRepository.findByShareId(readOnlyShare.shareId())).thenReturn(Optional.of(readOnlyShare));
        assertThatThrownBy(() -> service.requireAccess(
                MEMBER, readOnlyShare.shareId(), true, "trace_share_write_denied"))
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        ArgumentCaptor<SessionShareAuditEvent> audit = ArgumentCaptor.forClass(SessionShareAuditEvent.class);
        verify(shareRepository, org.mockito.Mockito.atLeastOnce()).appendAudit(audit.capture());
        assertThat(audit.getAllValues()).anySatisfy(event -> {
            assertThat(event.action()).isEqualTo("WRITE_ACCESS_DENIED");
            assertThat(event.outcome()).isEqualTo("DENIED");
            assertThat(event.errorCode()).isEqualTo(ErrorCode.FORBIDDEN.name());
            assertThat(event.actorUserId()).isEqualTo(MEMBER);
            assertThat(event.executionOwnerUserId()).isEqualTo(OWNER);
        });
    }

    @Test
    void ownerGetsRedirectContextEvenAfterShareExpiryButMemberDoesNot() {
        SessionShare expired = share(true, NOW.minusSeconds(1));
        when(shareRepository.findByShareId(expired.shareId())).thenReturn(Optional.of(expired));

        assertThat(service.requireAccess(OWNER, expired.shareId(), false, "trace_share_owner").ownerAccess()).isTrue();
        assertThatThrownBy(() -> service.requireAccess(
                MEMBER, expired.shareId(), false, "trace_share_expired"))
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.SESSION_SHARE_EXPIRED);
    }

    @Test
    void revokeUsesVersionCompareAndSetAndKeepsStableShareId() {
        SessionShare existing = share(true, NOW.plus(Duration.ofDays(1)));
        when(shareRepository.findBySessionId(SESSION)).thenReturn(Optional.of(existing));
        when(shareRepository.update(any(), eq(0L))).thenReturn(true);

        SessionShare revoked = service.revoke(OWNER, SESSION, 0L, "trace_share_revoke");

        assertThat(revoked.shareId()).isEqualTo(existing.shareId());
        assertThat(revoked.version()).isEqualTo(1);
        ArgumentCaptor<SessionShare> captor = ArgumentCaptor.forClass(SessionShare.class);
        verify(shareRepository).update(captor.capture(), eq(0L));
        assertThat(captor.getValue().status().name()).isEqualTo("REVOKED");
    }

    private Session session() {
        return new Session(
                SESSION, WORKSPACE, "分享会话", SessionStatus.ACTIVE, NOW, NOW,
                "trace_share_session", null, null, false, null, null, OWNER);
    }

    private SessionShare share(boolean canChat, Instant expiresAt) {
        return new SessionShare(
                new SessionShareId("shr_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                SESSION, WORKSPACE, OWNER, com.enterprise.testagent.domain.sessionshare.SessionShareStatus.ACTIVE,
                expiresAt, 0,
                List.of(com.enterprise.testagent.domain.sessionshare.SessionShareMembership.active(
                        MEMBER, "ucid-member", "协作人", canChat, NOW.minus(Duration.ofDays(1)))),
                NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(1)), null, "trace_share_existing");
    }

    private User user(UserId id, String unifiedAuthId, String username, UserStatus status) {
        return new User(id, unifiedAuthId, username, "hash", null, null, null, status, NOW, NOW);
    }
}
