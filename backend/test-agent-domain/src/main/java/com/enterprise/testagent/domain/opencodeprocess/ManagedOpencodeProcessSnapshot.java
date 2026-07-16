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
        String traceId) {
}
