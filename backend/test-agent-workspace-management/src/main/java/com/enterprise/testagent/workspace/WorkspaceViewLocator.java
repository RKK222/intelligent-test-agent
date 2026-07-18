package com.enterprise.testagent.workspace;

/**
 * 工作区组合视图逻辑定位器。REFERENCE 必须绑定当前配置中的别名，COMPOSITE/WORKSPACE 不接受别名。
 */
public record WorkspaceViewLocator(
        WorkspaceViewLocatorKind kind,
        String path,
        String referenceAlias) {

    /** 返回组合视图根定位器。 */
    public static WorkspaceViewLocator root() {
        return new WorkspaceViewLocator(WorkspaceViewLocatorKind.COMPOSITE, "", null);
    }
}
