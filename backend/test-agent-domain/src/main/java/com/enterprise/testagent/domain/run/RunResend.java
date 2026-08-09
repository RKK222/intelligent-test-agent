package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 原生撤销重发聚合。
 *
 * <p>该聚合只保存控制面身份、次数和安全错误摘要，禁止保存 prompt、附件正文、模型回答或工具输出。
 */
public record RunResend(
        RunResendId resendId,
        SessionId sessionId,
        UserId ownerUserId,
        RunId sourceRunId,
        RunId replacementRunId,
        String sourceRemoteMessageId,
        String replacementRemoteMessageId,
        RunResendTrigger trigger,
        int totalAttempt,
        int automaticAttempt,
        int automaticLimit,
        RunResendStatus status,
        Instant executeAt,
        String targetLinuxServerId,
        String leaseToken,
        Instant leaseUntil,
        String clientRequestId,
        String traceId,
        String safeErrorMessage,
        Instant createdAt,
        Instant updatedAt,
        UserId requesterUserId,
        String requesterUnifiedAuthId,
        boolean requestedBySharedUser) {

    public RunResend {
        Objects.requireNonNull(resendId, "resendId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(sourceRunId, "sourceRunId must not be null");
        Objects.requireNonNull(replacementRunId, "replacementRunId must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(status, "status must not be null");
        sourceRemoteMessageId = DomainValidation.requireText(sourceRemoteMessageId, "sourceRemoteMessageId");
        replacementRemoteMessageId = DomainValidation.requireText(
                replacementRemoteMessageId, "replacementRemoteMessageId");
        targetLinuxServerId = DomainValidation.requireText(targetLinuxServerId, "targetLinuxServerId");
        clientRequestId = DomainValidation.requireText(clientRequestId, "clientRequestId");
        traceId = DomainValidation.requireText(traceId, "traceId");
        executeAt = DomainValidation.requireInstant(executeAt, "executeAt");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (totalAttempt < 1 || automaticAttempt < 0 || automaticAttempt > totalAttempt) {
            throw new IllegalArgumentException("invalid resend attempt counters");
        }
        if (automaticLimit < 1 || automaticAttempt > automaticLimit) {
            throw new IllegalArgumentException("automatic attempt exceeds limit");
        }
        if ((leaseToken == null) != (leaseUntil == null)) {
            throw new IllegalArgumentException("leaseToken and leaseUntil must be set together");
        }
        if (status == RunResendStatus.REVERTING && leaseToken == null) {
            throw new IllegalArgumentException("REVERTING resend requires a lease");
        }
        if (leaseToken != null) {
            leaseToken = DomainValidation.requireText(leaseToken, "leaseToken");
        }
        if (safeErrorMessage != null) {
            safeErrorMessage = safeErrorMessage.strip();
            if (safeErrorMessage.length() > 512) {
                safeErrorMessage = safeErrorMessage.substring(0, 512);
            }
        }
        requesterUserId = requesterUserId == null ? ownerUserId : requesterUserId;
        requesterUnifiedAuthId = requesterUnifiedAuthId == null || requesterUnifiedAuthId.isBlank()
                ? null : requesterUnifiedAuthId.strip();
        if (requestedBySharedUser && requesterUnifiedAuthId == null) {
            throw new IllegalArgumentException("shared requester requires unifiedAuthId");
        }
    }

    /** 兼容代操作归因字段加入前的构造器；普通重发默认由所属人发起。 */
    public RunResend(
            RunResendId resendId,
            SessionId sessionId,
            UserId ownerUserId,
            RunId sourceRunId,
            RunId replacementRunId,
            String sourceRemoteMessageId,
            String replacementRemoteMessageId,
            RunResendTrigger trigger,
            int totalAttempt,
            int automaticAttempt,
            int automaticLimit,
            RunResendStatus status,
            Instant executeAt,
            String targetLinuxServerId,
            String leaseToken,
            Instant leaseUntil,
            String clientRequestId,
            String traceId,
            String safeErrorMessage,
            Instant createdAt,
            Instant updatedAt) {
        this(resendId, sessionId, ownerUserId, sourceRunId, replacementRunId,
                sourceRemoteMessageId, replacementRemoteMessageId, trigger, totalAttempt,
                automaticAttempt, automaticLimit, status, executeAt, targetLinuxServerId,
                leaseToken, leaseUntil, clientRequestId, traceId, safeErrorMessage, createdAt, updatedAt,
                ownerUserId, null, false);
    }

    /** 记录真实重发发起人；该身份只用于平台归因与权限，不传递给 OpenCode。 */
    public RunResend withRequester(UserId requester, String unifiedAuthId, boolean shared) {
        return new RunResend(
                resendId, sessionId, ownerUserId, sourceRunId, replacementRunId,
                sourceRemoteMessageId, replacementRemoteMessageId, trigger, totalAttempt,
                automaticAttempt, automaticLimit, status, executeAt, targetLinuxServerId,
                leaseToken, leaseUntil, clientRequestId, traceId, safeErrorMessage, createdAt, updatedAt,
                requester, unifiedAuthId, shared);
    }

    /** 用上一替代 Run 作为下一轮来源；人工重发不增加或重置自动额度。 */
    public RunResend nextAttempt(
            RunResendId nextResendId,
            RunId nextReplacementRunId,
            String nextReplacementRemoteMessageId,
            RunResendTrigger nextTrigger,
            Instant nextExecuteAt,
            String nextClientRequestId) {
        int nextAutomaticAttempt = automaticAttempt + (nextTrigger == RunResendTrigger.AUTOMATIC ? 1 : 0);
        if (nextAutomaticAttempt > automaticLimit) {
            throw new IllegalStateException("automatic resend limit exceeded");
        }
        return new RunResend(
                nextResendId,
                sessionId,
                ownerUserId,
                replacementRunId,
                nextReplacementRunId,
                replacementRemoteMessageId,
                nextReplacementRemoteMessageId,
                nextTrigger,
                Math.addExact(totalAttempt, 1),
                nextAutomaticAttempt,
                automaticLimit,
                RunResendStatus.WAITING,
                nextExecuteAt,
                targetLinuxServerId,
                null,
                null,
                nextClientRequestId,
                traceId,
                null,
                nextExecuteAt,
                nextExecuteAt,
                requesterUserId,
                requesterUnifiedAuthId,
                requestedBySharedUser);
    }

    public RunResend startReverting(String nextLeaseToken, Instant nextLeaseUntil, Instant now) {
        requireStatus(RunResendStatus.WAITING);
        if (nextLeaseUntil == null || !nextLeaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
        return copy(RunResendStatus.REVERTING, nextLeaseToken, nextLeaseUntil, null, now);
    }

    public RunResend markReverted(Instant now) {
        requireStatus(RunResendStatus.REVERTING);
        return copy(RunResendStatus.REVERTED, null, null, null, now);
    }

    /** REVERTING 租约过期后由恢复任务以旧 token 做 CAS 换租。 */
    public RunResend renewRevertingLease(String nextLeaseToken, Instant nextLeaseUntil, Instant now) {
        requireStatus(RunResendStatus.REVERTING);
        if (nextLeaseUntil == null || !nextLeaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
        return copy(RunResendStatus.REVERTING, nextLeaseToken, nextLeaseUntil, null, now);
    }

    public RunResend markDispatched(Instant now) {
        requireStatus(RunResendStatus.REVERTED);
        return copy(RunResendStatus.DISPATCHED, null, null, null, now);
    }

    public RunResend fail(String safeMessage, Instant now) {
        if (status.isTerminal()) {
            throw invalidTransition();
        }
        return copy(RunResendStatus.FAILED, null, null, safeMessage, now);
    }

    /** 只有尚未执行原生 revert 的等待记录允许由现有停止入口取消。 */
    public RunResend cancel(Instant now) {
        requireStatus(RunResendStatus.WAITING);
        return copy(RunResendStatus.CANCELLED, null, null, null, now);
    }

    private RunResend copy(
            RunResendStatus nextStatus,
            String nextLeaseToken,
            Instant nextLeaseUntil,
            String nextSafeError,
            Instant now) {
        return new RunResend(
                resendId, sessionId, ownerUserId, sourceRunId, replacementRunId,
                sourceRemoteMessageId, replacementRemoteMessageId, trigger, totalAttempt,
                automaticAttempt, automaticLimit, nextStatus, executeAt, targetLinuxServerId,
                nextLeaseToken, nextLeaseUntil, clientRequestId, traceId, nextSafeError,
                createdAt, now, requesterUserId, requesterUnifiedAuthId, requestedBySharedUser);
    }

    private void requireStatus(RunResendStatus required) {
        if (status != required) {
            throw invalidTransition();
        }
    }

    private IllegalStateException invalidTransition() {
        return new IllegalStateException("run resend status cannot transition from " + status);
    }
}
