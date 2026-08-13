package com.enterprise.testagent.domain.user;

import java.util.Objects;

/** 定时补偿候选用户；仅包含已配置 SSH Key 且仍有效的用户。 */
public record UserScmGitIdentityCandidate(
        UserId userId,
        String unifiedAuthId,
        UserScmGitIdentity currentIdentity) {

    public UserScmGitIdentityCandidate {
        Objects.requireNonNull(userId, "userId must not be null");
        unifiedAuthId = Objects.requireNonNull(unifiedAuthId, "unifiedAuthId must not be null").trim();
        if (unifiedAuthId.isEmpty()) {
            throw new IllegalArgumentException("unifiedAuthId must not be blank");
        }
    }
}
