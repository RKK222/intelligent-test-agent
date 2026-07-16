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
        String traceId) {
}
