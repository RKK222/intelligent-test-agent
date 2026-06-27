package com.icbc.testagent.domain.opencodeprocess;

import java.util.Objects;

/**
 * Redis 中的后端 Java 进程运行快照；TTL 表示在线，内容供运行管理和 manager socket 发现使用。
 */
public record BackendRuntimeSnapshot(
        LinuxServer linuxServer,
        BackendJavaProcess backendProcess,
        BackendRuntimeMetrics metrics) {

    /**
     * 兼容旧调用方，未采集资源指标时只保存后端拓扑快照。
     */
    public BackendRuntimeSnapshot(
            LinuxServer linuxServer,
            BackendJavaProcess backendProcess) {
        this(linuxServer, backendProcess, null);
    }

    /**
     * 校验快照必须同时包含服务器和 Java 进程，避免 Redis 中写入半截运行态。
     */
    public BackendRuntimeSnapshot {
        Objects.requireNonNull(linuxServer, "linuxServer must not be null");
        Objects.requireNonNull(backendProcess, "backendProcess must not be null");
    }
}
