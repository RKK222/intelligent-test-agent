package com.enterprise.testagent.system.management.localclient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 设置页和头像实例菜单使用的本地客户端状态投影。 */
public final class LocalClientInstanceResponses {

    private LocalClientInstanceResponses() {
    }

    public record InstanceView(
            String clientInstanceId,
            String clientName,
            String platform,
            String architecture,
            String clientVersion,
            String opencodeVersion,
            boolean online,
            long connectionGeneration,
            List<String> reportedAddresses,
            String observedRemoteAddress,
            Integer opencodePort,
            String processStatus,
            boolean opencodeHealthy,
            Long processId,
            Instant processStartedAt,
            Instant lastHeartbeatAt,
            Instant lastConnectedAt,
            Instant lastDisconnectedAt,
            Map<String, Boolean> capabilities,
            boolean selfUpdateSupported,
            String targetClientVersion,
            String updateDirection,
            String lastUpdateStatus,
            Instant lastUpdateAt,
            PublicCapabilitiesView publicCapabilities) {
    }

    public record PublicCapabilitiesView(
            boolean supported,
            String activeCommit,
            String activeDigest,
            String pendingCommit,
            String pendingDigest,
            String status,
            String errorCode,
            Integer agentCount,
            Integer skillCount,
            Integer toolCount,
            Boolean requiresRestart,
            String changeSummaryJson,
            Instant reportedAt,
            Instant updatedAt) {
    }
}
