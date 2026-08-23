package com.enterprise.testagent.domain.workspace;

/** 工作空间 Git 权限巡检的跨 Java 低敏广播契约。 */
public final class WorkspaceGitAccessInspectionEvents {

    public static final String LOCAL_CLIENT_INSPECTION_REQUESTED =
            "workspace.git-access-inspection-requested";

    private WorkspaceGitAccessInspectionEvents() {
    }
}
