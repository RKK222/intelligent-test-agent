package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * code_repositories 表行模型，仅在 persistence MyBatis 映射内部使用。
 */
public record CodeRepositoryRow(
        String repositoryId,
        String gitUrl,
        String name,
        String englishName,
        String repositoryType,
        String deploymentMode,
        boolean standard,
        Instant createdAt,
        Instant updatedAt) {
}
