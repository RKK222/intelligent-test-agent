package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 用户级会话运行态查询行模型，仅由 MyBatis 仓储内部使用。
 */
public record SessionRuntimeStateRow(
        String sessionId,
        String runId,
        String runStatus,
        String attention,
        String attentionEventId,
        Instant attentionAt,
        Instant updatedAt) {
}
