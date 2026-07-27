package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 工具点击明细 MyBatis 行模型；用户删除后 userId 可为空。 */
public record ToolboxClickEventRow(
        String eventId,
        String toolId,
        String source,
        String userId,
        String traceId,
        boolean counted,
        Instant clickedAt) {
}
