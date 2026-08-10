package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** session_share_memberships 的 MyBatis 行模型。 */
public record SessionShareMembershipRow(
        String shareId,
        String userId,
        String unifiedAuthId,
        String username,
        Boolean canChat,
        String status,
        Instant sharedAt,
        Instant updatedAt,
        Instant removedAt) {
}
