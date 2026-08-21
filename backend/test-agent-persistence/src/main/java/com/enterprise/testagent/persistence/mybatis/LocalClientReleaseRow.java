package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_releases 的 MyBatis 行模型。 */
public record LocalClientReleaseRow(
        String version,
        String platform,
        String architecture,
        int launcherVersionMin,
        int launcherVersionMax,
        String protocolVersion,
        String manifestUrl,
        String manifestSha256,
        String manifestSignature,
        boolean compatible,
        Instant publishedAt,
        Instant syncedAt) {
}
