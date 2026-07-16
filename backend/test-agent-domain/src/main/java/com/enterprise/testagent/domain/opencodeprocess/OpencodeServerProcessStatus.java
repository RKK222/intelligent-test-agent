package com.enterprise.testagent.domain.opencodeprocess;

/**
 * 用户专属 opencode server 进程状态。
 */
public enum OpencodeServerProcessStatus {
    STARTING,
    RUNNING,
    UNHEALTHY,
    STOPPED,
    FAILED
}
