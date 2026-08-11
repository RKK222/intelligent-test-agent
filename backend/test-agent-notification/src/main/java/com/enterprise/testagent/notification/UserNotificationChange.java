package com.enterprise.testagent.notification;

import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 节点内和跨节点共用的低敏通知变化信号，不携带通知正文。 */
public record UserNotificationChange(
        UserId recipientUserId,
        UserNotificationId notificationId,
        UserNotificationChangeType changeType,
        String traceId,
        Instant occurredAt) {

    public UserNotificationChange {
        Objects.requireNonNull(recipientUserId, "recipientUserId must not be null");
        Objects.requireNonNull(changeType, "changeType must not be null");
        traceId = traceId == null || traceId.isBlank() ? "trace_notification" : traceId;
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
