package com.enterprise.testagent.notification;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.notification.UserNotificationView;

/** 通知分页与全局未读数的组合结果。 */
public record UserNotificationPage(PageResponse<UserNotificationView> page, long unreadCount) {
}
