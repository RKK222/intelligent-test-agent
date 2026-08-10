package com.enterprise.testagent.domain.sessionshare;

/** 分享成员持久状态；分享过期、取消和会话归档由访问视图叠加计算。 */
public enum SessionShareMembershipStatus {
    ACTIVE,
    REMOVED
}
