package com.enterprise.testagent.domain.configuration;

/**
 * 创建应用工作空间时展示给前端的稳定步骤码和中文名称。
 */
public enum WorkspaceCreateOperationStep {
    VALIDATING_INPUT("校验参数"),
    SAVING_TEMPLATE("保存工作空间配置"),
    RESOLVING_VERSION("解析版本和分支"),
    PREPARING_REPOSITORY("下载代码并切换分支"),
    CREATING_RUNTIME_WORKSPACE("创建运行态工作区"),
    COMPLETED("完成");

    private final String displayName;

    WorkspaceCreateOperationStep(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
