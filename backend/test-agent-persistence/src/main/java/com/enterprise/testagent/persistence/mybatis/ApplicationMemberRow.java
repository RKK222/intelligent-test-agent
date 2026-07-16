package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * application_members 表行模型，仅在 persistence MyBatis 映射内部使用。
 */
public record ApplicationMemberRow(
        String appId,
        String userId,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
