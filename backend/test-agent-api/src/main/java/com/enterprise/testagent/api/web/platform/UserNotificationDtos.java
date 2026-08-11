package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.notification.UserNotificationView;
import com.enterprise.testagent.notification.UserNotificationPage;
import com.enterprise.testagent.notification.UserNotificationStreamUpdate;
import java.time.Instant;
import java.util.List;

/** 通知中心 HTTP/SSE DTO；不返回 dedupKey、内部数据库 ID 或任意跳转 URL。 */
final class UserNotificationDtos {

    private UserNotificationDtos() {
    }

    record UserNotificationResponse(
            String notificationId,
            String type,
            String actorUserId,
            String title,
            String body,
            String actionType,
            String actionTargetId,
            String status,
            String invalidationReason,
            boolean actionAvailable,
            boolean unread,
            Instant expiresAt,
            Instant readAt,
            Instant createdAt,
            Instant updatedAt) {

        static UserNotificationResponse from(UserNotificationView notification) {
            return new UserNotificationResponse(
                    notification.notificationId().value(),
                    notification.type().name(),
                    notification.actorUserId() == null ? null : notification.actorUserId().value(),
                    notification.title(),
                    notification.body(),
                    notification.actionType().name(),
                    notification.actionTargetId(),
                    notification.status().name(),
                    notification.invalidationReason(),
                    notification.actionAvailable(),
                    notification.unread(),
                    notification.expiresAt(),
                    notification.readAt(),
                    notification.createdAt(),
                    notification.updatedAt());
        }
    }

    record UserNotificationPageResponse(
            List<UserNotificationResponse> items,
            int page,
            int size,
            long total,
            long unreadCount) {

        static UserNotificationPageResponse from(UserNotificationPage result) {
            return new UserNotificationPageResponse(
                    result.page().items().stream().map(UserNotificationResponse::from).toList(),
                    result.page().page(),
                    result.page().size(),
                    result.page().total(),
                    result.unreadCount());
        }
    }

    record MarkReadResponse(String notificationId, boolean read) {
    }

    record UserNotificationStreamResponse(
            String changeType,
            String notificationId,
            long unreadCount,
            Instant generatedAt) {

        static UserNotificationStreamResponse from(UserNotificationStreamUpdate update) {
            return new UserNotificationStreamResponse(
                    update.changeType().name(),
                    update.notificationId() == null ? null : update.notificationId().value(),
                    update.unreadCount(),
                    update.generatedAt());
        }
    }
}
