package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;

/**
 * 已由当前服务器获得租约的公共个人 worktree 补偿任务。
 */
public record PublicAgentConfigWorktreeClaim(
        String rolloutId,
        String worktreeId,
        String userId,
        String linuxServerId,
        String targetCommit,
        String traceId,
        int retryCount,
        Instant leaseUntil,
        String leaseToken) {

    public PublicAgentConfigWorktreeClaim {
        rolloutId = DomainValidation.requireText(rolloutId, "rolloutId");
        worktreeId = DomainValidation.requireText(worktreeId, "worktreeId");
        userId = DomainValidation.requireText(userId, "userId");
        linuxServerId = DomainValidation.requireText(linuxServerId, "linuxServerId");
        targetCommit = DomainValidation.requireText(targetCommit, "targetCommit");
        traceId = DomainValidation.requireText(traceId, "traceId");
        leaseUntil = DomainValidation.requireInstant(leaseUntil, "leaseUntil");
        leaseToken = DomainValidation.requireText(leaseToken, "leaseToken");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
    }
}
