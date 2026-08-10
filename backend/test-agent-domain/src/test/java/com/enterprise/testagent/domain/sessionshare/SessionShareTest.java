package com.enterprise.testagent.domain.sessionshare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 会话协作分享聚合边界测试。 */
class SessionShareTest {

    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");

    @Test
    void createsActiveShareWithStableScopeAndMembers() {
        SessionShare share = SessionShare.create(
                new SessionShareId("shr_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                new SessionId("ses_share_domain"),
                new WorkspaceId("wrk_share_domain"),
                new UserId("usr_owner_domain"),
                NOW.plus(Duration.ofDays(3)),
                List.of(member("usr_member_one", true)),
                NOW,
                "trace_share_domain");

        assertThat(share.status()).isEqualTo(SessionShareStatus.ACTIVE);
        assertThat(share.version()).isZero();
        assertThat(share.memberships()).singleElement().satisfies(member -> {
            assertThat(member.canChat()).isTrue();
            assertThat(member.status()).isEqualTo(SessionShareMembershipStatus.ACTIVE);
        });
        assertThat(share.activeAt(NOW.plusSeconds(1))).isTrue();
    }

    @Test
    void rejectsExpiryBeyondSevenDaysAndMoreThanFiftyMembers() {
        assertThatThrownBy(() -> SessionShare.create(
                shareId(), sessionId(), workspaceId(), ownerId(), NOW.plus(Duration.ofDays(7)).plusMillis(1),
                List.of(member("usr_member_one", true)), NOW, "trace_share_expiry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7 days");

        List<SessionShareMembership> members = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            members.add(member("usr_member_" + index, index % 2 == 0));
        }
        assertThatThrownBy(() -> SessionShare.create(
                shareId(), sessionId(), workspaceId(), ownerId(), NOW.plus(Duration.ofDays(1)),
                members, NOW, "trace_share_limit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    @Test
    void fullUpdateSoftRemovesMissingMembersAndIncrementsVersion() {
        SessionShare original = SessionShare.create(
                shareId(), sessionId(), workspaceId(), ownerId(), NOW.plus(Duration.ofDays(1)),
                List.of(member("usr_member_one", false), member("usr_member_two", true)),
                NOW, "trace_share_update");

        SessionShare updated = original.update(
                NOW.plus(Duration.ofDays(2)),
                List.of(member("usr_member_two", false), member("usr_member_three", true)),
                NOW.plusSeconds(60),
                "trace_share_update_2");

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.memberships()).extracting(it -> it.userId().value())
                .containsExactlyInAnyOrder("usr_member_one", "usr_member_two", "usr_member_three");
        assertThat(updated.membership(new UserId("usr_member_one"))).get()
                .extracting(SessionShareMembership::status)
                .isEqualTo(SessionShareMembershipStatus.REMOVED);
        assertThat(updated.membership(new UserId("usr_member_two"))).get()
                .satisfies(it -> assertThat(it.canChat()).isFalse());
    }

    @Test
    void revokeAndReactivateReuseShareIdAndHistoricalMemberships() {
        SessionShare original = SessionShare.create(
                shareId(), sessionId(), workspaceId(), ownerId(), NOW.plus(Duration.ofDays(1)),
                List.of(member("usr_member_one", true)), NOW, "trace_share_revoke");

        SessionShare revoked = original.revoke(NOW.plusSeconds(30), "trace_share_revoked");
        SessionShare reactivated = revoked.reactivate(
                NOW.plus(Duration.ofDays(4)),
                List.of(member("usr_member_two", true)),
                NOW.plusSeconds(60),
                "trace_share_reactivated");

        assertThat(reactivated.shareId()).isEqualTo(original.shareId());
        assertThat(reactivated.status()).isEqualTo(SessionShareStatus.ACTIVE);
        assertThat(reactivated.version()).isEqualTo(2);
        assertThat(reactivated.memberships()).extracting(it -> it.userId().value())
                .containsExactlyInAnyOrder("usr_member_one", "usr_member_two");
        assertThat(reactivated.membership(new UserId("usr_member_one"))).get()
                .extracting(SessionShareMembership::status)
                .isEqualTo(SessionShareMembershipStatus.REMOVED);
    }

    private SessionShareMembership member(String userId, boolean canChat) {
        return SessionShareMembership.active(
                new UserId(userId), "ucid-" + userId, "姓名-" + userId, canChat, NOW);
    }

    private SessionShareId shareId() {
        return new SessionShareId("shr_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }

    private SessionId sessionId() {
        return new SessionId("ses_share_domain");
    }

    private WorkspaceId workspaceId() {
        return new WorkspaceId("wrk_share_domain");
    }

    private UserId ownerId() {
        return new UserId("usr_owner_domain");
    }
}
