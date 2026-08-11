package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** user_notifications 的原始持久化行模型。 */
public record UserNotificationRow(
        String notificationId,
        String recipientUserId,
        String type,
        String actorUserId,
        String title,
        String body,
        String actionType,
        String actionTargetId,
        String dedupKey,
        String status,
        String invalidationReason,
        Instant expiresAt,
        Instant readAt,
        Instant invalidatedAt,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {
}
