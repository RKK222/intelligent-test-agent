package com.enterprise.testagent.domain.localclient;

/** 客户端实际观测到的本地 OpenCode 进程状态。 */
public enum LocalClientProcessStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    UNHEALTHY,
    FAILED
}
