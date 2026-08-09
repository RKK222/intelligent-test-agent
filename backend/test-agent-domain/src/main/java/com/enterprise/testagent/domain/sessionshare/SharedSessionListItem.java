package com.enterprise.testagent.domain.sessionshare;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/** “分享给我”列表项，包含失效历史但不授予普通工作区成员关系。 */
public record SharedSessionListItem(
        SessionShareId shareId,
        SessionId sessionId,
        WorkspaceId workspaceId,
        String sessionTitle,
        UserId ownerUserId,
        String ownerUnifiedAuthId,
        String ownerUsername,
        Instant sharedAt,
        Instant expiresAt,
        boolean canChat,
        SessionShareAccessStatus status) {

    public SharedSessionListItem {
        Objects.requireNonNull(shareId, "shareId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        sessionTitle = DomainValidation.requireText(sessionTitle, "sessionTitle");
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        ownerUnifiedAuthId = DomainValidation.requireText(ownerUnifiedAuthId, "ownerUnifiedAuthId");
        ownerUsername = DomainValidation.requireText(ownerUsername, "ownerUsername");
        sharedAt = DomainValidation.requireInstant(sharedAt, "sharedAt");
        expiresAt = DomainValidation.requireInstant(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status must not be null");
    }
}
