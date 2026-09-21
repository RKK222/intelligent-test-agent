package com.enterprise.testagent.api.web.platform;

import java.nio.file.Path;
import java.time.Instant;

/** 协调节点签发并在首次 WebSocket 握手时消费的 shard 接收票据。 */
record TeamWorkspaceExportShardTicket(
        String value,
        String sourceLinuxServerId,
        Path target,
        long maxArchiveBytes,
        Instant expiresAt) {
}
