package com.enterprise.testagent.domain.sessionshare;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Objects;

/** 分享工作台的参与人目录；只包含展示和权限所需的最小身份字段。 */
public record SessionShareParticipant(
        UserId userId,
        String unifiedAuthId,
        String username,
        boolean owner,
        boolean canChat,
        SessionShareMembershipStatus membershipStatus) {

    public SessionShareParticipant {
        Objects.requireNonNull(userId, "userId must not be null");
        unifiedAuthId = DomainValidation.requireText(unifiedAuthId, "unifiedAuthId");
        username = DomainValidation.requireText(username, "username");
        if (!owner) {
            Objects.requireNonNull(membershipStatus, "membershipStatus must not be null");
        }
    }
}
