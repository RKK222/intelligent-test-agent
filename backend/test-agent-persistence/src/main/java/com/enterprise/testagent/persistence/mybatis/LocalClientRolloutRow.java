package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 本地客户端灰度名单关系型行。 */
public record LocalClientRolloutRow(
        String userId,
        boolean enabled,
        String updatedByUserId,
        Instant createdAt,
        Instant updatedAt) {
}
