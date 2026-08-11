package com.enterprise.testagent.domain.notification;

/** 通知可执行动作类型；服务端仅按受控类型拼接目标，不保存任意 URL。 */
public enum UserNotificationActionType {
    SESSION_SHARE,
    NONE,
    RESTART_OWN_PROCESS
}
