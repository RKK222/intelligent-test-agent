package com.enterprise.testagent.domain.notification;

/** 用户通知业务类型；Agent 配置 dispose 使用类型表达同一通知行的当前状态。 */
public enum UserNotificationType {
    SESSION_SHARED,
    AGENT_CONFIG_DISPOSE_PENDING,
    AGENT_CONFIG_DISPOSE_SUCCEEDED,
    AGENT_CONFIG_DISPOSE_FAILED,
    AGENT_CONFIG_DISPOSE_SUPERSEDED,
    LOCAL_CLIENT_UPDATE_AVAILABLE
}
