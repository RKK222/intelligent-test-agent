package com.enterprise.testagent.domain.managedworkspace;

/**
 * 应用版本工作区服务器副本同步状态。
 */
public enum WorkspaceReplicaSyncStatus {
    PENDING,
    SYNCING,
    READY,
    FAILED
}
