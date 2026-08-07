package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendReplayInputStore;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.event.RunEventAppender;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 复用现有停止按钮取消尚未开始原生 revert 的 WAITING 重发。 */
@Service
public class RunResendCancellationService {

    private final RunResendRepository resendRepository;
    private final RunResendReplayInputStore inputStore;
    private final RunRepository runRepository;
    private final RunEventAppender eventAppender;
    private final Clock clock;

    public RunResendCancellationService(
            RunResendRepository resendRepository,
            RunResendReplayInputStore inputStore,
            RunRepository runRepository,
            RunEventAppender eventAppender,
            Clock clock) {
        this.resendRepository = Objects.requireNonNull(resendRepository);
        this.inputStore = Objects.requireNonNull(inputStore);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.eventAppender = Objects.requireNonNull(eventAppender);
        this.clock = Objects.requireNonNull(clock);
    }

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
        resendRepository.deleteSessionLock(waiting.sessionId(), waiting.resendId());
        inputStore.delete(replacementRunId);
        eventAppender.append(new RunEventDraft(
                replacementRunId,
                RunEventType.RUN_CANCELLED,
                traceId,
                now,
                Map.of("status", RunStatus.CANCELLED.name(), "resendId", waiting.resendId().value())),
                RunStorageMode.LEGACY_FULL);
        return Optional.of(cancelledRun);
    }
}
