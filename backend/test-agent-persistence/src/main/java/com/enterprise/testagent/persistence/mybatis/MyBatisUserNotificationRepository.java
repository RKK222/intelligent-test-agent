package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.notification.UserNotification;
import com.enterprise.testagent.domain.notification.UserNotificationActionType;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.notification.UserNotificationRepository;
import com.enterprise.testagent.domain.notification.UserNotificationStatus;
import com.enterprise.testagent.domain.notification.UserNotificationType;
import com.enterprise.testagent.domain.notification.UserNotificationView;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 用户通知 MyBatis 仓储实现。 */
@Repository
public class MyBatisUserNotificationRepository implements UserNotificationRepository {

    private final UserNotificationMapper mapper;

    public MyBatisUserNotificationRepository(UserNotificationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public boolean insert(UserNotification notification) {
        return mapper.insertNotification(toRow(notification)) == 1;
    }

    @Override
    public boolean updateByDedupKeyIfChanged(UserNotification notification) {
        return mapper.updateByDedupKeyIfChanged(toRow(notification)) == 1;
    }

    @Override
    public boolean reactivateByDedupKeyIfChanged(UserNotification notification) {
        return mapper.reactivateByDedupKeyIfChanged(toRow(notification)) == 1;
    }

    @Override
    public int updateActiveByAction(
            UserId recipientUserId,
            UserNotificationActionType actionType,
            String actionTargetId,
            UserId actorUserId,
            String title,
            String body,
            Instant expiresAt,
            String traceId,
            Instant updatedAt) {
        return mapper.updateActiveByAction(
                recipientUserId.value(), actionType.name(), actionTargetId,
                actorUserId == null ? null : actorUserId.value(), title, body,
                expiresAt, traceId, updatedAt);
    }

    @Override
    public List<UserId> findActiveRecipientsByAction(
            UserNotificationActionType actionType,
            String actionTargetId,
            UserId recipientUserId) {
        return mapper.findActiveRecipientsByAction(
                        actionType.name(), actionTargetId,
                        recipientUserId == null ? null : recipientUserId.value()).stream()
                .map(UserId::new)
                .toList();
    }

    @Override
    public List<UserId> findActiveSessionShareRecipientsBySession(SessionId sessionId) {
        return mapper.findActiveSessionShareRecipientsBySession(sessionId.value()).stream()
                .map(UserId::new)
                .toList();
    }

    @Override
    public int invalidateActiveByAction(
            UserNotificationActionType actionType,
            String actionTargetId,
            UserId recipientUserId,
            String reason,
            String traceId,
            Instant invalidatedAt) {
        return mapper.invalidateActiveByAction(
                actionType.name(), actionTargetId,
                recipientUserId == null ? null : recipientUserId.value(),
                reason, traceId, invalidatedAt);
    }

    @Override
    public int invalidateSessionSharesBySession(
            SessionId sessionId,
            String reason,
            String traceId,
            Instant invalidatedAt) {
        return mapper.invalidateSessionSharesBySession(
                sessionId.value(), reason, traceId, invalidatedAt);
    }

    @Override
    public boolean invalidateActiveById(
            UserNotificationId notificationId,
            UserId recipientUserId,
            String reason,
            String traceId,
            Instant invalidatedAt) {
        return mapper.invalidateActiveById(
                notificationId.value(), recipientUserId.value(), reason, traceId, invalidatedAt) == 1;
    }

    @Override
    public Optional<UserNotification> findByIdForRecipient(
            UserNotificationId notificationId,
            UserId recipientUserId) {
        return Optional.ofNullable(mapper.findByIdForRecipient(
                notificationId.value(), recipientUserId.value())).map(this::toDomain);
    }

    @Override
    public PageResponse<UserNotificationView> findPage(
            UserId recipientUserId,
            boolean unreadOnly,
            Instant now,
            PageRequest pageRequest) {
        List<UserNotificationView> items = mapper.findPage(
                        recipientUserId.value(), unreadOnly, now,
                        pageRequest.size(), pageRequest.offset()).stream()
                .map(this::toView)
                .toList();
        return new PageResponse<>(
                items,
                pageRequest.page(),
                pageRequest.size(),
                mapper.countPage(recipientUserId.value(), unreadOnly, now));
    }

    @Override
    public long countUnread(UserId recipientUserId, Instant now) {
        return mapper.countUnread(recipientUserId.value(), now);
    }

    @Override
    public boolean markReadById(
            UserNotificationId notificationId,
            UserId recipientUserId,
            Instant readAt,
            String traceId) {
        return mapper.markReadById(
                notificationId.value(), recipientUserId.value(), readAt, traceId) == 1;
    }

    @Override
    public boolean markReadByAction(
            UserId recipientUserId,
            UserNotificationActionType actionType,
            String actionTargetId,
            Instant readAt,
            String traceId) {
        return mapper.markReadByAction(
                recipientUserId.value(), actionType.name(), actionTargetId, readAt, traceId) > 0;
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        return mapper.deleteCreatedBefore(cutoff);
    }

    private UserNotificationRow toRow(UserNotification notification) {
        return new UserNotificationRow(
                notification.notificationId().value(),
                notification.recipientUserId().value(),
                notification.type().name(),
                notification.actorUserId() == null ? null : notification.actorUserId().value(),
                notification.title(),
                notification.body(),
                notification.actionType().name(),
                notification.actionTargetId(),
                notification.dedupKey(),
                notification.status().name(),
                notification.invalidationReason(),
                notification.expiresAt(),
                notification.readAt(),
                notification.invalidatedAt(),
                notification.traceId(),
                notification.createdAt(),
                notification.updatedAt());
    }

    private UserNotification toDomain(UserNotificationRow row) {
        return new UserNotification(
                new UserNotificationId(row.notificationId()),
                new UserId(row.recipientUserId()),
                UserNotificationType.valueOf(row.type()),
                row.actorUserId() == null ? null : new UserId(row.actorUserId()),
                row.title(), row.body(),
                UserNotificationActionType.valueOf(row.actionType()),
                row.actionTargetId(), row.dedupKey(),
                UserNotificationStatus.valueOf(row.status()),
                row.invalidationReason(), row.expiresAt(), row.readAt(), row.invalidatedAt(),
                row.traceId(), row.createdAt(), row.updatedAt());
    }

    private UserNotificationView toView(UserNotificationViewRow row) {
        return new UserNotificationView(
                new UserNotificationId(row.notificationId()),
                UserNotificationType.valueOf(row.type()),
                row.actorUserId() == null ? null : new UserId(row.actorUserId()),
                row.title(), row.body(),
                UserNotificationActionType.valueOf(row.actionType()),
                row.actionTargetId(),
                UserNotificationStatus.valueOf(row.effectiveStatus()),
                row.effectiveInvalidationReason(),
                Boolean.TRUE.equals(row.actionAvailable()),
                Boolean.TRUE.equals(row.unread()),
                row.expiresAt(), row.readAt(), row.createdAt(), row.updatedAt());
    }
}
