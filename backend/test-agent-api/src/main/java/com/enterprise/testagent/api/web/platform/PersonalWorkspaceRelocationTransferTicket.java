package com.enterprise.testagent.api.web.platform;

import java.time.Instant;

/** 只绑定单次搬迁归档、源服务器和目标服务器的一次性内部 WebSocket ticket。 */
record PersonalWorkspaceRelocationTransferTicket(
        String ticket,
        String relocationId,
        String sourceLinuxServerId,
        String targetLinuxServerId,
        String snapshotSha256,
        long archiveSizeBytes,
        String traceId,
        Instant expiresAt) {
}
