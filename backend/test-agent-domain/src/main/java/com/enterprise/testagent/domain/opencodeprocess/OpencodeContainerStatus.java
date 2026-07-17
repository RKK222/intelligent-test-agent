package com.enterprise.testagent.domain.opencodeprocess;

/**
 * opencode 容器状态，只有 READY 且有剩余容量时可参与后续调度。
 */
public enum OpencodeContainerStatus {
    READY,
    BUSY,
    UNHEALTHY,
    OFFLINE
}
