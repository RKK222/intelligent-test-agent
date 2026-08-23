package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_public_capability_releases 的 MyBatis 行模型。 */
public record LocalClientPublicCapabilityReleaseRow(
        String sourceCommit,
        String bundleDigest,
        String artifactSha256,
        String compatibility,
        String errorCode,
        String manifestJson,
        String changeSummaryJson,
        int agentCount,
        int skillCount,
        int toolCount,
        boolean requiresRestart,
        byte[] artifact,
        long compressedSize,
        long uncompressedSize,
        int fileCount,
        Instant createdAt) {
}
