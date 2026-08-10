package com.enterprise.testagent.notification;

/** 用户通知实时变化类型；SNAPSHOT 同时用于首帧和周期性权威校准。 */
public enum UserNotificationChangeType {
    SNAPSHOT,
    CREATED,
    READ,
    UPDATED,
    INVALIDATED
}
