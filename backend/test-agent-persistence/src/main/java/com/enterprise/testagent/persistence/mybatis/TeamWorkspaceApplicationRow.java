package com.enterprise.testagent.persistence.mybatis;

/** 团队可见应用查询行。 */
public record TeamWorkspaceApplicationRow(
        String appId,
        String appName,
        boolean enabled,
        long currentMemberCount,
        long historicalMemberCount,
        String membershipState) {
}
