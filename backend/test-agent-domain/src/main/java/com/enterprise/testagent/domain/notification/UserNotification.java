package com.enterprise.testagent.domain.notification;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 单个接收人的持久通知事实。
 *
 * <p>title/body 只能保存安全展示快照；actionTargetId 由 actionType 解释，不能当作 URL 使用。
 */
public record UserNotification(
        UserNotificationId notificationId,
        UserId recipientUserId,
        UserNotificationType type,
        UserId actorUserId,
        String title,
        String body,
        UserNotificationActionType actionType,
        String actionTargetId,
        String dedupKey,
        UserNotificationStatus status,
        String invalidationReason,
        Instant expiresAt,
        Instant readAt,
        Instant invalidatedAt,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    /** 校验通知边界，防止任意大文本或不完整失效状态进入持久化。 */
    public UserNotification {
        Objects.requireNonNull(notificationId, "notificationId must not be null");
        Objects.requireNonNull(recipientUserId, "recipientUserId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        title = boundedText(title, "title", 200);
        body = boundedText(body, "body", 500);
        Objects.requireNonNull(actionType, "actionType must not be null");
        actionTargetId = boundedText(actionTargetId, "actionTargetId", 128);
        dedupKey = boundedText(dedupKey, "dedupKey", 256);
        Objects.requireNonNull(status, "status must not be null");
        traceId = boundedText(traceId, "traceId", 128);
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (status == UserNotificationStatus.INVALIDATED
                && (invalidationReason == null || invalidationReason.isBlank() || invalidatedAt == null)) {
            throw new IllegalArgumentException("invalidated notification requires reason and invalidatedAt");
        }
        if (status == UserNotificationStatus.ACTIVE && invalidatedAt != null) {
            throw new IllegalArgumentException("active notification must not have invalidatedAt");
        }
    }

    private static String boundedText(String value, String fieldName, int maxLength) {
        String required = DomainValidation.requireText(value, fieldName);
        if (required.codePointCount(0, required.length()) > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return required;
    }
}
