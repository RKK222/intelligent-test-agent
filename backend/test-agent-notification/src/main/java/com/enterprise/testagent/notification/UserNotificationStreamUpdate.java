package com.enterprise.testagent.notification;

import com.enterprise.testagent.domain.notification.UserNotificationId;
import java.time.Instant;

/** 用户通知 SSE 的当前状态帧；列表正文继续通过分页 HTTP 获取。 */
public record UserNotificationStreamUpdate(
        UserNotificationChangeType changeType,
        UserNotificationId notificationId,
        long unreadCount,
        Instant generatedAt) {
}
