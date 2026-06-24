package com.icbc.testagent.domain.opencodeprocess;

/**
 * 后端 Java 进程状态，表示管理进程可连接的后端实例是否在线。
 */
public enum BackendJavaProcessStatus {
    READY,
    UNHEALTHY,
    OFFLINE
}
