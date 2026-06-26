package com.icbc.testagent.domain.configuration;

/**
 * Agent 配置操作的稳定步骤码，前端用该枚举展示步骤条。
 */
public enum AgentConfigOperationStep {
    VALIDATING("校验"),
    PREPARING_REPOSITORY("准备 Git 仓库"),
    UPDATING_FILES("更新文件"),
    CREATING_WORKTREE("创建 worktree"),
    STAGING("暂存变更"),
    COMMITTING("提交"),
    MERGING("合并"),
    PUSHING("发布"),
    BROADCASTING("广播同步"),
    COMPLETED("完成");

    private final String displayName;

    AgentConfigOperationStep(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
