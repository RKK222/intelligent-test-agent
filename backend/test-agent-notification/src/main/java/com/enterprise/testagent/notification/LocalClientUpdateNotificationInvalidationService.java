package com.enterprise.testagent.notification;

import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在用户更新请求外独立提交陈旧通知失效，确保随后抛出的 409 不会回滚该精确失效。 */
@Service
public class LocalClientUpdateNotificationInvalidationService {

    private final UserNotificationApplicationService notifications;

    public LocalClientUpdateNotificationInvalidationService(UserNotificationApplicationService notifications) {
        this.notifications = Objects.requireNonNull(notifications, "notifications must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void invalidate(
            UserId recipientUserId,
            UserNotificationId notificationId,
            String reason,
            String traceId) {
        notifications.invalidateLocalClientUpdateNotification(recipientUserId, notificationId, reason, traceId);
    }
}
