package com.enterprise.testagent.domain.notification;

import com.enterprise.testagent.domain.support.DomainValidation;

/** 用户站内通知外部 ID；生产与历史回填均固定使用 {@code ntf_} 前缀。 */
public record UserNotificationId(String value) {

    public UserNotificationId {
        value = DomainValidation.requirePrefixedId(value, "ntf_", "notificationId");
    }
}
