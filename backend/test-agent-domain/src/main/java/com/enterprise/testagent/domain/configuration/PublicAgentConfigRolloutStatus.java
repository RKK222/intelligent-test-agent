package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.List;

/**
 * 最近一次公共 Agent/Skill 全局 rollout 状态；供超管页面轮询，不替代后台租约处理。
 */
public record PublicAgentConfigRolloutStatus(
        String rolloutId,
        String status,
        String branch,
        String commitHash,
        String failureReason,
        String supersedesRolloutId,
        String supersededByRolloutId,
        String supersedeReason,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<PublicAgentConfigRolloutServerStatus> servers,
        AgentConfigRolloutScope configScope,
        String scopeKey) {

    /** 兼容只展示公共发布的既有调用。 */
    public PublicAgentConfigRolloutStatus(
            String rolloutId,
            String status,
            String branch,
            String commitHash,
            String failureReason,
            String supersedesRolloutId,
            String supersededByRolloutId,
            String supersedeReason,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            List<PublicAgentConfigRolloutServerStatus> servers) {
        this(
                rolloutId, status, branch, commitHash, failureReason,
                supersedesRolloutId, supersededByRolloutId, supersedeReason,
                createdAt, updatedAt, completedAt, servers,
                AgentConfigRolloutScope.PUBLIC, null);
    }

    public PublicAgentConfigRolloutStatus {
        servers = servers == null ? List.of() : List.copyOf(servers);
    }

    /** PREPARING 与 DRAINING 都持有公共全局锁。 */
    public boolean active() {
        return "PREPARING".equals(status) || "DRAINING".equals(status);
    }

    /** 组合仓储分两次只读查询取得的 rollout 主记录与服务器明细。 */
    public PublicAgentConfigRolloutStatus withServers(List<PublicAgentConfigRolloutServerStatus> serverStatuses) {
        return new PublicAgentConfigRolloutStatus(
                rolloutId,
                status,
                branch,
                commitHash,
                failureReason,
                supersedesRolloutId,
                supersededByRolloutId,
                supersedeReason,
                createdAt,
                updatedAt,
                completedAt,
                serverStatuses,
                configScope,
                scopeKey);
    }
}
