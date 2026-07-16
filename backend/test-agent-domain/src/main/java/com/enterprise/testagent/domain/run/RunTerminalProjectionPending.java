package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * Redis 终态事件原子发布的关系型投影 outbox。
 *
 * <p>记录只包含终态控制字段和可在投影前再次清洗的错误说明，不包含 prompt、完整回答、reasoning、
 * 工具输入输出或原始事件。version 用于防止旧投影完成后误确认晚到的新终态。
 */
public record RunTerminalProjectionPending(
        RunId runId,
        String producerLinuxServerId,
        long version,
        RunStatus status,
        String terminalSource,
        String terminalReasonCode,
        String safeErrorMessage,
        boolean remoteStopConfirmed,
        String traceId,
        Instant occurredAt) {

    public RunTerminalProjectionPending {
        Objects.requireNonNull(runId, "runId must not be null");
        producerLinuxServerId = DomainValidation.requireText(
                producerLinuxServerId, "producerLinuxServerId");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (status == null || !status.isTerminal()) {
            throw new IllegalArgumentException("status must be terminal");
        }
        terminalSource = DomainValidation.requireText(terminalSource, "terminalSource");
        terminalReasonCode = DomainValidation.requireText(terminalReasonCode, "terminalReasonCode");
        safeErrorMessage = safeErrorMessage == null || safeErrorMessage.isBlank()
                ? null
                : safeErrorMessage;
        traceId = DomainValidation.requireText(traceId, "traceId");
        occurredAt = DomainValidation.requireInstant(occurredAt, "occurredAt");
    }
}
