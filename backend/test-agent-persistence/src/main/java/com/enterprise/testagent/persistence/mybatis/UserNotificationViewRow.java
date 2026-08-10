package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 通知列表的有效状态查询行，已合并分享、成员、会话和到期事实。 */
public record UserNotificationViewRow(
        String notificationId,
        String type,
        String actorUserId,
        String title,
        String body,
        String actionType,
        String actionTargetId,
        String effectiveStatus,
        String effectiveInvalidationReason,
        Boolean actionAvailable,
        Boolean unread,
        Instant expiresAt,
        Instant readAt,
        Instant createdAt,
        Instant updatedAt) {
}
