package com.enterprise.testagent.workspace;

/**
 * 工作区组合视图逻辑定位器。REFERENCE 必须绑定当前配置中的别名，COMPOSITE/WORKSPACE 不接受别名。
 */
public record WorkspaceViewLocator(
        WorkspaceViewLocatorKind kind,
        String path,
        String referenceAlias,
        String automationAppId,
        String automationRepositoryId,
        Long automationGeneration) {

    /** 兼容既有工作区和应用资产引用定位器。 */
    public WorkspaceViewLocator(WorkspaceViewLocatorKind kind, String path, String referenceAlias) {
        this(kind, path, referenceAlias, null, null, null);
    }

    /** 返回组合视图根定位器。 */
    public static WorkspaceViewLocator root() {
        return new WorkspaceViewLocator(WorkspaceViewLocatorKind.COMPOSITE, "", null, null, null, null);
    }
}
