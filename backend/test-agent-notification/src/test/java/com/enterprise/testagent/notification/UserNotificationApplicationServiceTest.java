package com.enterprise.testagent.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.notification.UserNotification;
import com.enterprise.testagent.domain.notification.UserNotificationActionType;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.notification.UserNotificationRepository;
import com.enterprise.testagent.domain.notification.UserNotificationStatus;
import com.enterprise.testagent.domain.notification.UserNotificationType;
import com.enterprise.testagent.domain.notification.UserNotificationView;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShare;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembership;
import com.enterprise.testagent.domain.sessionshare.SessionShareStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserNotificationApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");
    private static final UserId OWNER = new UserId("usr_notification_owner");
    private static final UserId MEMBER = new UserId("usr_notification_member");
    private static final UserId OTHER = new UserId("usr_notification_other");
    private static final SessionShareId SHARE_ID = new SessionShareId(
            "shr_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    private static final SessionId SESSION_ID = new SessionId("ses_notification_share");

    private UserNotificationRepository repository;
    private CapturingPublisher publisher;
    private UserNotificationApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserNotificationRepository.class);
        publisher = new CapturingPublisher();
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        when(identity.instanceId()).thenReturn("java-a");
        when(identity.linuxServerId()).thenReturn("linux-a");
        UserNotificationRealtimeHub hub = new UserNotificationRealtimeHub(publisher, identity);
        service = new UserNotificationApplicationService(
                repository, hub, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsOneNotificationPerNewMemberAndBroadcastsOnlyLowSensitivityIdentifiers() {
        when(repository.findActiveRecipientsByAction(any(), eq(SHARE_ID.value()), any()))
                .thenReturn(List.of());
        when(repository.insert(any())).thenReturn(true);

        service.syncSessionShare(
                null,
                share(List.of(active(MEMBER, false), active(OTHER, true)), 0),
                "登录失败排查",
                "会话所属人",
                false,
                "trace_notification_create");

        ArgumentCaptor<UserNotification> inserted = ArgumentCaptor.forClass(UserNotification.class);
        verify(repository, org.mockito.Mockito.times(2)).insert(inserted.capture());
        assertThat(inserted.getAllValues())
                .extracting(UserNotification::recipientUserId)
                .containsExactlyInAnyOrder(MEMBER, OTHER);
        assertThat(inserted.getAllValues()).allSatisfy(notification -> {
            assertThat(notification.actionType()).isEqualTo(UserNotificationActionType.SESSION_SHARE);
            assertThat(notification.actionTargetId()).isEqualTo(SHARE_ID.value());
            assertThat(notification.title()).isEqualTo("会话所属人 向你分享了对话");
            assertThat(notification.body()).contains("登录失败排查");
        });
        assertThat(publisher.events).hasSize(2).allSatisfy(event -> {
            assertThat(event.type()).isEqualTo(UserNotificationRealtimeHub.BROADCAST_TYPE);
            assertThat(event.payload()).containsOnlyKeys(
                    "recipientUserId", "changeType", "notificationId");
            assertThat(event.payload()).doesNotContainKeys(
                    "title", "body", "actionTargetId", "shareId", "expiresAt");
        });
    }

    @Test
    void updatesStableMembershipInvalidatesRemovedMembershipAndReactivatesWithNewGeneration() {
        SessionShare previous = share(List.of(active(MEMBER, false), active(OTHER, true)), 2);
        SessionShare updated = share(List.of(active(MEMBER, true), active(OTHER, true).remove(NOW)), 3);
        when(repository.updateActiveByAction(eq(MEMBER), any(), eq(SHARE_ID.value()),
                eq(OWNER), any(), any(), any(), any(), eq(NOW))).thenReturn(1);
        when(repository.findActiveRecipientsByAction(
                UserNotificationActionType.SESSION_SHARE, SHARE_ID.value(), OTHER))
                .thenReturn(List.of(OTHER));
        when(repository.invalidateActiveByAction(
                UserNotificationActionType.SESSION_SHARE, SHARE_ID.value(), OTHER,
                "REMOVED", "trace_notification_update", NOW)).thenReturn(1);

        service.syncSessionShare(
                previous, updated, "登录失败排查", "会话所属人", false,
                "trace_notification_update");

        verify(repository).updateActiveByAction(
                eq(MEMBER), eq(UserNotificationActionType.SESSION_SHARE), eq(SHARE_ID.value()),
                eq(OWNER), any(), eq("登录失败排查 · 可对话"), eq(updated.expiresAt()),
                eq("trace_notification_update"), eq(NOW));
        verify(repository).invalidateActiveByAction(
                UserNotificationActionType.SESSION_SHARE, SHARE_ID.value(), OTHER,
                "REMOVED", "trace_notification_update", NOW);
        assertThat(publisher.events)
                .extracting(event -> event.payload().get("changeType"))
                .contains("UPDATED", "INVALIDATED");

        publisher.events.clear();
        when(repository.findActiveRecipientsByAction(
                UserNotificationActionType.SESSION_SHARE, SHARE_ID.value(), null))
                .thenReturn(List.of(MEMBER));
        when(repository.invalidateActiveByAction(
                UserNotificationActionType.SESSION_SHARE, SHARE_ID.value(), null,
                "REACTIVATED", "trace_notification_reactivate", NOW)).thenReturn(1);
        when(repository.insert(any())).thenReturn(true);

        service.syncSessionShare(
                previous, updated, "登录失败排查", "会话所属人", true,
                "trace_notification_reactivate");

        verify(repository).invalidateActiveByAction(
                UserNotificationActionType.SESSION_SHARE, SHARE_ID.value(), null,
                "REACTIVATED", "trace_notification_reactivate", NOW);
        verify(repository, org.mockito.Mockito.atLeastOnce()).insert(any());
    }

    @Test
    void genericMarkReadRejectsUnreadShareButKeepsAnAlreadyReadShareIdempotent() {
        UserNotification unread = notification(null);
        UserNotification read = notification(NOW.minusSeconds(1));
        when(repository.findByIdForRecipient(unread.notificationId(), MEMBER))
                .thenReturn(Optional.of(unread));

        assertThatThrownBy(() -> service.markRead(
                MEMBER, unread.notificationId(), "trace_notification_read"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("分享通知需在分享访问成功后标记已读");

        verify(repository, never()).markReadById(
                unread.notificationId(), MEMBER, NOW, "trace_notification_read");
        assertThat(publisher.events).isEmpty();

        when(repository.findByIdForRecipient(read.notificationId(), MEMBER))
                .thenReturn(Optional.of(read));
        service.markRead(MEMBER, read.notificationId(), "trace_notification_read_again");
        verify(repository, never()).markReadById(
                read.notificationId(), MEMBER, NOW, "trace_notification_read_again");
    }

    @Test
    void listUsesAuthorityUnreadCount() {
        UserNotification unread = notification(null);

        UserNotificationView view = new UserNotificationView(
                unread.notificationId(), UserNotificationType.SESSION_SHARED, OWNER,
                unread.title(), unread.body(), unread.actionType(), unread.actionTargetId(),
                UserNotificationStatus.ACTIVE, null, true, true,
                unread.expiresAt(), null, unread.createdAt(), unread.updatedAt());
        PageRequest request = new PageRequest(1, 20);
        when(repository.findPage(MEMBER, true, NOW, request))
                .thenReturn(new PageResponse<>(List.of(view), 1, 20, 1));
        when(repository.countUnread(MEMBER, NOW)).thenReturn(7L);

        UserNotificationPage page = service.list(MEMBER, true, request);

        assertThat(page.page().items()).containsExactly(view);
        assertThat(page.unreadCount()).isEqualTo(7L);
    }

    @Test
    void marksShareReadByActionAndDeletesHistoryStrictlyBeforeNinetyDays() {
        when(repository.markReadByAction(
                MEMBER, UserNotificationActionType.SESSION_SHARE, SHARE_ID.value(),
                NOW, "trace_share_access")).thenReturn(true);

        service.markSessionShareRead(MEMBER, SHARE_ID, "trace_share_access");
        service.deleteExpiredNotifications();

        verify(repository).markReadByAction(
                MEMBER, UserNotificationActionType.SESSION_SHARE, SHARE_ID.value(),
                NOW, "trace_share_access");
        verify(repository).deleteCreatedBefore(NOW.minus(Duration.ofDays(90)));
    }

    @Test
    void createsOnePendingDisposeNotificationWithOnlyTheRolloutAsItsTarget() {
        when(repository.updateByDedupKeyIfChanged(any())).thenReturn(false);
        when(repository.insert(any())).thenReturn(true);

        service.syncAgentConfigDispose(
                MEMBER,
                "acr_dispose_12345678",
                UserNotificationType.AGENT_CONFIG_DISPOSE_PENDING,
                "trace_dispose_pending");

        ArgumentCaptor<UserNotification> notification = ArgumentCaptor.forClass(UserNotification.class);
        verify(repository).insert(notification.capture());
        assertThat(notification.getValue()).satisfies(value -> {
            assertThat(value.type()).isEqualTo(UserNotificationType.AGENT_CONFIG_DISPOSE_PENDING);
            assertThat(value.actionType()).isEqualTo(UserNotificationActionType.NONE);
            assertThat(value.actionTargetId()).isEqualTo("acr_dispose_12345678");
            assertThat(value.dedupKey()).isEqualTo(
                    "AGENT_CONFIG_DISPOSE:acr_dispose_12345678:" + MEMBER.value());
            assertThat(value.body()).doesNotContain("trace", "/", "Exception");
        });
        assertThat(publisher.events).singleElement().satisfies(event ->
                assertThat(event.payload().get("changeType")).isEqualTo("CREATED"));
    }

    @Test
    void sameDisposeStateDoesNotBroadcastButFailureEnablesControlledRestart() {
        when(repository.updateByDedupKeyIfChanged(any()))
                .thenReturn(false, false, true);
        when(repository.insert(any())).thenReturn(false);

        service.syncAgentConfigDispose(
                MEMBER,
                "acr_dispose_12345678",
                UserNotificationType.AGENT_CONFIG_DISPOSE_PENDING,
                "trace_dispose_pending_retry");
        assertThat(publisher.events).isEmpty();

        service.syncAgentConfigDispose(
                MEMBER,
                "acr_dispose_12345678",
                UserNotificationType.AGENT_CONFIG_DISPOSE_FAILED,
                "trace_dispose_failed");

        ArgumentCaptor<UserNotification> notification = ArgumentCaptor.forClass(UserNotification.class);
        verify(repository, org.mockito.Mockito.times(3)).updateByDedupKeyIfChanged(notification.capture());
        assertThat(notification.getAllValues().get(2).actionType())
                .isEqualTo(UserNotificationActionType.RESTART_OWN_PROCESS);
        assertThat(publisher.events).singleElement().satisfies(event ->
                assertThat(event.payload().get("changeType")).isEqualTo("UPDATED"));
    }

    @Test
    void noneActionNotificationCanBeMarkedReadByItsRecipient() {
        UserNotification pending = new UserNotification(
                new UserNotificationId("ntf_dispose_pending"),
                MEMBER,
                UserNotificationType.AGENT_CONFIG_DISPOSE_PENDING,
                null,
                "Agent 配置等待生效",
                "配置已更新，正在等待当前任务结束后应用。",
                UserNotificationActionType.NONE,
                "acr_dispose_12345678",
                "AGENT_CONFIG_DISPOSE:acr_dispose_12345678:" + MEMBER.value(),
                UserNotificationStatus.ACTIVE,
                null,
                null,
                null,
                null,
                "trace_dispose_pending",
                NOW,
                NOW);
        when(repository.findByIdForRecipient(pending.notificationId(), MEMBER)).thenReturn(Optional.of(pending));
        when(repository.markReadById(
                pending.notificationId(), MEMBER, NOW, "trace_dispose_read")).thenReturn(true);

        service.markRead(MEMBER, pending.notificationId(), "trace_dispose_read");

        verify(repository).markReadById(pending.notificationId(), MEMBER, NOW, "trace_dispose_read");
        assertThat(publisher.events).singleElement().satisfies(event ->
                assertThat(event.payload().get("changeType")).isEqualTo("READ"));
    }

    private SessionShare share(List<SessionShareMembership> memberships, long version) {
        return new SessionShare(
                SHARE_ID,
                SESSION_ID,
                new WorkspaceId("wrk_notification_share"),
                OWNER,
                SessionShareStatus.ACTIVE,
                NOW.plus(Duration.ofDays(7)),
                version,
                memberships,
                NOW.minus(Duration.ofDays(1)),
                NOW,
                null,
                "trace_notification_share");
    }

    private SessionShareMembership active(UserId userId, boolean canChat) {
        return SessionShareMembership.active(
                userId, "ucid-" + userId.value(), "用户" + userId.value(), canChat,
                NOW.minus(Duration.ofHours(1)));
    }

    private UserNotification notification(Instant readAt) {
        return new UserNotification(
                new UserNotificationId(readAt == null ? "ntf_unread" : "ntf_read"),
                MEMBER,
                UserNotificationType.SESSION_SHARED,
                OWNER,
                "会话所属人 向你分享了对话",
                "登录失败排查 · 只读",
                UserNotificationActionType.SESSION_SHARE,
                SHARE_ID.value(),
                "SESSION_SHARE:" + SHARE_ID.value() + ":" + (readAt == null ? "unread" : "read"),
                UserNotificationStatus.ACTIVE,
                null,
                NOW.plus(Duration.ofDays(1)),
                readAt,
                null,
                "trace_notification",
                NOW.minus(Duration.ofHours(1)),
                NOW.minus(Duration.ofHours(1)));
    }

    private static final class CapturingPublisher implements ServerBroadcastPublisher {
        private final java.util.ArrayList<ServerBroadcastEvent> events = new java.util.ArrayList<>();

        @Override
        public String instanceId() {
            return "java-a";
        }

        @Override
        public void publish(ServerBroadcastEvent event) {
            events.add(event);
        }
    }
}
