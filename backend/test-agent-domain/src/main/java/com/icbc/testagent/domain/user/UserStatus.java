package com.icbc.testagent.domain.user;

/**
 * 用户状态枚举，表示用户账户的可用状态。
 */
public enum UserStatus {
    /**
     * 活跃状态，用户可正常登录和使用系统。
     */
    ACTIVE,
    /**
     * 停用状态，用户无法登录系统。
     */
    INACTIVE
}
