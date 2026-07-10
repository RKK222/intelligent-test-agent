package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 一次终态事务所需的完整投影；CAS 成功后同时写 Run 终态、最多两条摘要和 Session 时间。
 */
public record RunTerminalProjection(
        RunId runId,
        SessionId sessionId,
        RunStatus status,
        long expectedStatusVersion,
        String terminalSource,
        String terminalReasonCode,
        String safeErrorMessage,
        boolean remoteStopConfirmed,
        long lastEventSeq,
        Instant detailsExpiresAt,
        String rootRemoteSessionId,
        RunDiffCounts diffCounts,
        String lastRemoteMessageId,
        String lastRemotePartId,
        TokenUsage tokenUsage,
        BigDecimal costUsd,
        String traceId,
        Instant updatedAt,
        String agentId,
        ConversationSourceType sourceType,
        String sourceRefId,
        UserId senderUserId,
        List<RunConversationSummary> summaries) {

    public RunTerminalProjection {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (status == null || !status.isTerminal()) {
            throw new IllegalArgumentException("status must be terminal");
        }
        if (expectedStatusVersion < 0 || lastEventSeq < 0) {
            throw new IllegalArgumentException("terminal counters must not be negative");
        }
        terminalSource = bounded(terminalSource, "terminalSource", 64, false);
        terminalReasonCode = bounded(terminalReasonCode, "terminalReasonCode", 128, true);
        safeErrorMessage = bounded(safeErrorMessage, "safeErrorMessage", 1_024, true);
        detailsExpiresAt = DomainValidation.requireInstant(detailsExpiresAt, "detailsExpiresAt");
        rootRemoteSessionId = bounded(rootRemoteSessionId, "rootRemoteSessionId", 128, true);
        diffCounts = diffCounts == null ? RunDiffCounts.empty() : diffCounts;
        lastRemoteMessageId = bounded(lastRemoteMessageId, "lastRemoteMessageId", 128, true);
        lastRemotePartId = bounded(lastRemotePartId, "lastRemotePartId", 128, true);
        tokenUsage = tokenUsage == null ? TokenUsage.empty() : tokenUsage;
        if (costUsd != null && costUsd.signum() < 0) {
            throw new IllegalArgumentException("costUsd must not be negative");
        }
        traceId = bounded(traceId, "traceId", 128, false);
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        agentId = bounded(agentId, "agentId", 64, false).toLowerCase(java.util.Locale.ROOT);
        sourceType = sourceType == null ? ConversationSourceType.MANUAL : sourceType;
        sourceRefId = bounded(sourceRefId, "sourceRefId", 128, true);
        summaries = summaries == null ? List.of() : List.copyOf(summaries);
        if (summaries.size() > 2) {
            throw new IllegalArgumentException("terminal projection supports at most two summaries");
        }
        validateSummaryUniqueness(summaries);
    }

    private static void validateSummaryUniqueness(List<RunConversationSummary> summaries) {
        var roles = new HashSet<com.icbc.testagent.domain.session.SessionMessageRole>();
        var keys = new HashSet<String>();
        var messageIds = new HashSet<String>();
        for (RunConversationSummary summary : summaries) {
            Objects.requireNonNull(summary, "summary must not be null");
            if (!roles.add(summary.role())) {
                throw new IllegalArgumentException("terminal summaries must have distinct roles");
            }
            if (!keys.add(summary.summaryKey())) {
                throw new IllegalArgumentException("terminal summaries must have distinct summary keys");
            }
            if (!messageIds.add(summary.messageId().value())) {
                throw new IllegalArgumentException("terminal summaries must have distinct message IDs");
            }
        }
    }

    private static String bounded(String value, String fieldName, int maxLength, boolean optional) {
        if (value == null && optional) {
            return null;
        }
        String text = DomainValidation.requireText(value, fieldName);
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return text;
    }
}
