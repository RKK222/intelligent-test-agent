package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 工具累计点击 MyBatis 行模型。 */
public record ToolboxClickTotalRow(
        String toolId,
        long clickCount,
        Instant lastCountedAt,
        Instant updatedAt) {
}
