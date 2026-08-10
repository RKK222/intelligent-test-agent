package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** “分享给我”联表查询行模型。 */
public record SharedSessionListRow(
        String shareId,
        String sessionId,
        String workspaceId,
        String sessionTitle,
        String ownerUserId,
        String ownerUnifiedAuthId,
        String ownerUsername,
        Instant sharedAt,
        Instant expiresAt,
        Boolean canChat,
        String accessStatus) {
}
