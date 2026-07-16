package com.enterprise.testagent.persistence.mybatis;

/**
 * 组织维度用户规模计数，用于补齐组织排行中的注册/可用用户数。
 */
public record AnalyticsOrganizationUserCountRow(
        String name,
        long registeredUsers,
        long enabledUsers) {
}
