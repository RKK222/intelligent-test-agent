package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 本地客户端下载入口灰度名单的关系型行，避免与版本管理 rollout 混用。 */
public record LocalClientRolloutUserRow(
        String userId,
        boolean enabled,
        String updatedByUserId,
        Instant createdAt,
        Instant updatedAt) {
}
