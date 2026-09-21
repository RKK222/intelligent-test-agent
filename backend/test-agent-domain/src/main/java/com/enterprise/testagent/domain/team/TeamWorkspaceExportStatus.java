package com.enterprise.testagent.domain.team;

/** 整组工作区 ZIP 导出的稳定生命周期。 */
public enum TeamWorkspaceExportStatus {
    QUEUED,
    RUNNING,
    READY,
    PARTIAL_READY,
    FAILED,
    CANCELLED,
    EXPIRED
}
