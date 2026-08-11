package com.enterprise.testagent.domain.notification;

/** 通知持久状态；过期属于查询时派生失效，不批量改写历史行。 */
public enum UserNotificationStatus {
    ACTIVE,
    INVALIDATED
}
