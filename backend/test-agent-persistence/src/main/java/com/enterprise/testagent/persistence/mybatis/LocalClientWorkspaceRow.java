package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_workspaces 的 MyBatis 行模型。 */
public record LocalClientWorkspaceRow(
        String workspaceId,
        String userId,
        String clientInstanceId,
        String normalizedRootPath,
        String rootDigest,
        String fileSystemIdentity,
        Instant createdAt,
        Instant updatedAt) {
}
