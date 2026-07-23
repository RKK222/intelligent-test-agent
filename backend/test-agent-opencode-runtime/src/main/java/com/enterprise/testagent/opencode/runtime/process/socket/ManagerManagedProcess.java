package com.enterprise.testagent.opencode.runtime.process.socket;

import java.time.Instant;

/**
 * manager 心跳中上报的本地 opencode server 进程明细。
 */
public record ManagerManagedProcess(
        int port,
        Long pid,
        String baseUrl,
        String sessionPath,
        String configPath,
        Instant startedAt,
        String startCommand,
        String traceId,
        String unifiedAuthId,
        String managerStatus) {

    /** 兼容旧 manager payload、旧测试和旧构造调用。 */
    public ManagerManagedProcess(
            int port,
            Long pid,
            String baseUrl,
            String sessionPath,
            String configPath,
            Instant startedAt,
            String startCommand,
            String traceId) {
        this(port, pid, baseUrl, sessionPath, configPath, startedAt, startCommand, traceId, null, null);
    }
}
