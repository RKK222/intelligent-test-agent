package com.enterprise.testagent.domain.opencodeprocess;

import java.time.Instant;

/**
 * manager latest snapshot 中保存的本容器 opencode server 进程明细，暂不参与运行管理趋势展示。
 */
public record ManagedOpencodeProcessSnapshot(
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

    /** 兼容旧 Redis JSON 及既有构造调用，新增字段缺失时保持 null。 */
    public ManagedOpencodeProcessSnapshot(
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
