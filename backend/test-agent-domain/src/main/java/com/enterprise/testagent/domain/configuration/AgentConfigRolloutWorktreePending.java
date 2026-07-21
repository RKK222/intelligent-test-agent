package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * 应用配置发布时暂不能合入目标提交的个人 worktree。
 *
 * <p>该记录只保存业务 ID 和稳定原因码，不携带物理路径，供发布协调器持久化并异步补偿。</p>
 */
public record AgentConfigRolloutWorktreePending(
        String personalWorkspaceId,
        String userId,
        String reason) {

    public AgentConfigRolloutWorktreePending {
        personalWorkspaceId = DomainValidation.requireText(personalWorkspaceId, "personalWorkspaceId");
        userId = DomainValidation.requireText(userId, "userId");
        reason = DomainValidation.requireText(reason, "reason");
    }
}
