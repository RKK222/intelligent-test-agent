package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_user_version_policies 的 MyBatis 行模型。 */
public record LocalClientUserPolicyRow(
        String userId,
        String targetVersion,
        long revision,
        String updatedBy,
        Instant updatedAt) {
}
