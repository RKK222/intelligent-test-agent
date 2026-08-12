package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendDetailCleanupPort;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.domain.run.RunResendPolicy;
import com.enterprise.testagent.domain.run.RunResendReplayInputStore;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunResendTrigger;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证 WAITING 重发取消后撤销提前展示的 USER，并在提交后通知共享参与者恢复源轮次。 */
class RunResendCancellationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
    private static final SessionId SESSION_ID = new SessionId("ses_resend_cancel");
    private static final RunId SOURCE_RUN_ID = new RunId("run_resend_cancel_source");
    private static final RunId REPLACEMENT_RUN_ID = new RunId("run_resend_cancel_replacement");

    @Test
    void cancellationRemovesPendingReplacementAndPublishesNewRevision() {
        RunResendRepository resendRepository = mock(RunResendRepository.class);
        RunResendReplayInputStore inputStore = mock(RunResendReplayInputStore.class);
        RunRepository runRepository = mock(RunRepository.class);
        RunResendDetailCleanupPort cleanupPort = mock(RunResendDetailCleanupPort.class);
        RunEventAppender eventAppender = mock(RunEventAppender.class);
        SessionMessageRealtimeHub realtimeHub = mock(SessionMessageRealtimeHub.class);
        RunResend waiting = waiting();
        Run replacement = new Run(
                REPLACEMENT_RUN_ID, SESSION_ID, new WorkspaceId("wrk_resend_cancel"),
                RunStatus.PENDING, NOW.minusSeconds(1), NOW.minusSeconds(1), "trace_resend_cancel");
        when(resendRepository.findByReplacementRunId(REPLACEMENT_RUN_ID))
                .thenReturn(Optional.of(waiting));
        when(resendRepository.saveIfStatus(any(), any())).thenReturn(true);
        when(runRepository.findById(REPLACEMENT_RUN_ID)).thenReturn(Optional.of(replacement));
        when(runRepository.saveIfStatus(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        RunResendCancellationService service = new RunResendCancellationService(
                resendRepository, inputStore, runRepository, cleanupPort, eventAppender,
                realtimeHub, Clock.fixed(NOW, ZoneOffset.UTC));

        Optional<Run> cancelled = service.cancelWaiting(REPLACEMENT_RUN_ID, "trace_resend_cancel");

        assertThat(cancelled).get().extracting(Run::status).isEqualTo(RunStatus.CANCELLED);
        verify(cleanupPort).purgePendingReplacementMessage(REPLACEMENT_RUN_ID, SESSION_ID, NOW);
        verify(inputStore).delete(REPLACEMENT_RUN_ID);
        verify(realtimeHub).publishAfterCommit(org.mockito.ArgumentMatchers.argThat(change ->
                change.sessionId().equals(SESSION_ID)
                        && change.sourceRunId().equals(SOURCE_RUN_ID)
                        && change.replacementRunId().equals(REPLACEMENT_RUN_ID)
                        && change.revision().equals(NOW)));
    }

    private RunResend waiting() {
        return new RunResend(
                new RunResendId("rsd_resend_cancel"), SESSION_ID, new UserId("usr_resend_cancel"),
                SOURCE_RUN_ID, REPLACEMENT_RUN_ID,
                "msg_resend_cancel_source", "msg_resend_cancel_replacement",
                RunResendTrigger.MANUAL, 1, 0, RunResendPolicy.MAX_AUTOMATIC_ATTEMPTS,
                RunResendStatus.WAITING, NOW, "linux-resend-cancel", null, null,
                "request_resend_cancel", "trace_resend_cancel", null, NOW.minusSeconds(1), NOW.minusSeconds(1));
    }
}
