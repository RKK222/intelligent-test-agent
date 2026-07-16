package com.enterprise.testagent.persistence.mybatis;

import java.math.BigDecimal;
import java.time.Instant;

/** Run 终态 CAS 的 MyBatis 行参数；不包含任何对话原文或事件 payload。 */
public record RunTerminalProjectionRow(
        String runId,
        String sessionId,
        String status,
        long expectedStatusVersion,
        String terminalSource,
        String terminalReasonCode,
        String safeErrorMessage,
        boolean remoteStopConfirmed,
        long lastEventSeq,
        Instant detailsExpiresAt,
        String rootRemoteSessionId,
        int diffProposedCount,
        int diffAcceptedCount,
        int diffRejectedCount,
        String lastRemoteMessageId,
        String lastRemotePartId,
        Long tokensInput,
        Long tokensOutput,
        Long tokensReasoning,
        Long tokensCacheRead,
        Long tokensCacheWrite,
        BigDecimal costUsd,
        Instant updatedAt) {
}
