package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_global_version_policy 的 MyBatis 行模型。 */
public record LocalClientGlobalPolicyRow(
        String targetVersion,
        long revision,
        String updatedBy,
        Instant updatedAt) {
}
