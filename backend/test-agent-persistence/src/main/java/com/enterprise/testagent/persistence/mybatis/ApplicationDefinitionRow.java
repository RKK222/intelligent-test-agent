package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * applications 表行模型，仅在 persistence MyBatis 映射内部使用。
 */
public record ApplicationDefinitionRow(
        String appId,
        String appName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
