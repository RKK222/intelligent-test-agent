package com.enterprise.testagent.domain.sessionshare;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Objects;

/** 普通用户可见的最小分享候选人资料。 */
public record SessionShareCandidate(UserId userId, String unifiedAuthId, String username) {

    public SessionShareCandidate {
        Objects.requireNonNull(userId, "userId must not be null");
        unifiedAuthId = DomainValidation.requireText(unifiedAuthId, "unifiedAuthId");
        username = DomainValidation.requireText(username, "username");
    }
}
