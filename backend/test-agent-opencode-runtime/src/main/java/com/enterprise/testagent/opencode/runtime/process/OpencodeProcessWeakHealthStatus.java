package com.enterprise.testagent.opencode.runtime.process;

/**
 * 弱健康检查状态；只表达当前探测结果，不参与数据库进程状态流转。
 */
public enum OpencodeProcessWeakHealthStatus {
    HEALTHY,
    UNHEALTHY,
    PROCESS_NOT_FOUND,
    MANAGER_UNAVAILABLE,
    BACKEND_UNAVAILABLE
}
