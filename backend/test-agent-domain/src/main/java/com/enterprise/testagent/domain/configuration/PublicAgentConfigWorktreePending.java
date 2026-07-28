package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * 公共 Git 刷新时暂时无法合入目标提交的个人 worktree。
 */
public record PublicAgentConfigWorktreePending(
        String worktreeId,
        String userId,
        String reason) {

    public PublicAgentConfigWorktreePending {
        worktreeId = DomainValidation.requireText(worktreeId, "worktreeId");
        userId = DomainValidation.requireText(userId, "userId");
        reason = DomainValidation.requireText(reason, "reason");
    }
}
