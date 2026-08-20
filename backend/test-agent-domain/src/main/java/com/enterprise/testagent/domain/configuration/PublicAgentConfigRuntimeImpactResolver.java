package com.enterprise.testagent.domain.configuration;

/**
 * 判断一次已同步的 Agent 配置发布是否必须重建 OpenCode 进程。
 *
 * <p>Git 路径和提交差异由工作区模块解释；运行时模块只消费影响结论，避免反向读取配置仓库。</p>
 */
public interface PublicAgentConfigRuntimeImpactResolver {

    /** Tool JavaScript/TypeScript 模块使用进程级 ESM 缓存，变更后不能只依赖 {@code /global/dispose}。 */
    boolean requiresProcessRestart(
            AgentConfigRolloutScope scope,
            String scopeKey,
            String previousCommitHash,
            String commitHash);
}
