package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendDetailCleanupPort;
import com.enterprise.testagent.domain.run.RunResendReplayInputStore;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub.SessionMessageChange;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub.SessionMessageChangeType;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 复用现有停止按钮取消尚未开始原生 revert 的 WAITING 重发。 */
@Service
public class RunResendCancellationService {

    private final RunResendRepository resendRepository;
    private final RunResendReplayInputStore inputStore;
    private final RunRepository runRepository;
    private final RunResendDetailCleanupPort cleanupPort;
    private final RunEventAppender eventAppender;
    private final SessionMessageRealtimeHub sessionMessageRealtimeHub;
    private final Clock clock;

    public RunResendCancellationService(
            RunResendRepository resendRepository,
            RunResendReplayInputStore inputStore,
            RunRepository runRepository,
            RunResendDetailCleanupPort cleanupPort,
            RunEventAppender eventAppender,
            SessionMessageRealtimeHub sessionMessageRealtimeHub,
            Clock clock) {
        this.resendRepository = Objects.requireNonNull(resendRepository);
        this.inputStore = Objects.requireNonNull(inputStore);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.cleanupPort = Objects.requireNonNull(cleanupPort);
        this.eventAppender = Objects.requireNonNull(eventAppender);
        this.sessionMessageRealtimeHub = Objects.requireNonNull(sessionMessageRealtimeHub);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public Optional<Run> cancelWaiting(RunId replacementRunId, String traceId) {
        RunResend waiting = resendRepository.findByReplacementRunId(replacementRunId)
                .filter(resend -> resend.status() == RunResendStatus.WAITING)
                .orElse(null);
        if (waiting == null) return Optional.empty();
        Instant now = clock.instant();
        RunResend cancelled = waiting.cancel(now);
        if (!resendRepository.saveIfStatus(cancelled, RunResendStatus.WAITING)) {
            return Optional.empty();
        }
        Run run = runRepository.findById(replacementRunId).orElseThrow();
        Run cancelledRun = run.status() == RunStatus.PENDING
                ? runRepository.saveIfStatus(run.requestCancel(now), RunStatus.PENDING)
                : run;
        cleanupPort.purgePendingReplacementMessage(replacementRunId, waiting.sessionId(), now);
        resendRepository.deleteSessionLock(waiting.sessionId(), waiting.resendId());
        inputStore.delete(replacementRunId);
        eventAppender.append(new RunEventDraft(
                replacementRunId,
                RunEventType.RUN_CANCELLED,
                traceId,
                now,
                Map.of("status", RunStatus.CANCELLED.name(), "resendId", waiting.resendId().value())),
                RunStorageMode.LEGACY_FULL);
        sessionMessageRealtimeHub.publishAfterCommit(new SessionMessageChange(
                waiting.sessionId(), waiting.sourceRunId(), replacementRunId,
                SessionMessageChangeType.RESEND_RESTORED,
                now, traceId, now));
        return Optional.of(cancelledRun);
    }
}
