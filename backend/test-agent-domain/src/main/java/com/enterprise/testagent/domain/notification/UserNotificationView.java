package com.enterprise.testagent.domain.notification;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;

/** 通知列表的有效状态投影；actionAvailable/unread 已合并分享、会话和到期事实。 */
public record UserNotificationView(
        UserNotificationId notificationId,
        UserNotificationType type,
        UserId actorUserId,
        String title,
        String body,
        UserNotificationActionType actionType,
        String actionTargetId,
        UserNotificationStatus status,
        String invalidationReason,
        boolean actionAvailable,
        boolean unread,
        Instant expiresAt,
        Instant readAt,
        Instant createdAt,
        Instant updatedAt) {
}
