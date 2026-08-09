package com.enterprise.testagent.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RunResendTest {

    private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");

    @Test
    void automaticRetryUsesOneTwoFourMinuteBackoffAndStopsAfterThreeAttempts() {
        assertThat(RunResendPolicy.automaticDelay(1)).hasMinutes(1);
        assertThat(RunResendPolicy.automaticDelay(2)).hasMinutes(2);
        assertThat(RunResendPolicy.automaticDelay(3)).hasMinutes(4);
        assertThat(RunResendPolicy.canScheduleAutomatic(2)).isTrue();
        assertThat(RunResendPolicy.canScheduleAutomatic(3)).isFalse();
        assertThatThrownBy(() -> RunResendPolicy.automaticDelay(4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3");
    }

    @Test
    void manualResendIncrementsTotalAttemptWithoutResettingAutomaticQuota() {
        RunResend automatic = waiting(RunResendTrigger.AUTOMATIC, 2, 2);

        RunResend manual = automatic.nextAttempt(
                new RunResendId("rsd_2234567890abcdef"),
                new RunId("run_3234567890abcdef"),
                "msg_3234567890abcdef",
                RunResendTrigger.MANUAL,
                NOW.plusSeconds(120),
                "req_manual_2");

        assertThat(manual.totalAttempt()).isEqualTo(3);
        assertThat(manual.automaticAttempt()).isEqualTo(2);
        assertThat(manual.executeAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(RunResendPolicy.canScheduleAutomatic(manual.automaticAttempt())).isTrue();
    }

    @Test
    void lifecycleOnlyAllowsWaitingRevertingRevertedDispatchedOrder() {
        RunResend waiting = waiting(RunResendTrigger.MANUAL, 1, 0);
        RunResend reverting = waiting.startReverting("lease_123", NOW.plusSeconds(30), NOW);
        RunResend reverted = reverting.markReverted(NOW.plusSeconds(1));
        RunResend dispatched = reverted.markDispatched(NOW.plusSeconds(2));

        assertThat(reverting.status()).isEqualTo(RunResendStatus.REVERTING);
        assertThat(reverted.status()).isEqualTo(RunResendStatus.REVERTED);
        assertThat(dispatched.status()).isEqualTo(RunResendStatus.DISPATCHED);
        assertThatThrownBy(() -> waiting.markDispatched(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WAITING");
    }

    @Test
    void onlyWaitingResendCanBeCancelledBeforeNativeRevert() {
        RunResend waiting = waiting(RunResendTrigger.AUTOMATIC, 1, 1);

        assertThat(waiting.cancel(NOW).status()).isEqualTo(RunResendStatus.CANCELLED);
        assertThatThrownBy(() -> waiting.startReverting("lease_123", NOW.plusSeconds(30), NOW).cancel(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REVERTING");
    }

    @Test
    void requesterAttributionSurvivesStateTransitionsAndNextAttempt() {
        UserId requester = new UserId("usr_shared_requester");
        RunResend resend = waiting(RunResendTrigger.MANUAL, 1, 0)
                .withRequester(requester, "ucid-shared-requester", true);

        RunResend reverting = resend.startReverting("lease_shared", NOW.plusSeconds(30), NOW);
        RunResend next = resend.nextAttempt(
                new RunResendId("rsd_3234567890abcdef"),
                new RunId("run_4234567890abcdef"),
                "msg_4234567890abcdef", RunResendTrigger.MANUAL,
                NOW.plusSeconds(60), "req_shared_2");

        assertThat(reverting.requesterUserId()).isEqualTo(requester);
        assertThat(next.requesterUnifiedAuthId()).isEqualTo("ucid-shared-requester");
        assertThat(next.requestedBySharedUser()).isTrue();
    }

    private static RunResend waiting(RunResendTrigger trigger, int totalAttempt, int automaticAttempt) {
        return new RunResend(
                new RunResendId("rsd_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new UserId("usr_1234567890abcdef"),
                new RunId("run_1234567890abcdef"),
                new RunId("run_2234567890abcdef"),
                "msg_1234567890abcdef",
                "msg_2234567890abcdef",
                trigger,
                totalAttempt,
                automaticAttempt,
                RunResendPolicy.MAX_AUTOMATIC_ATTEMPTS,
                RunResendStatus.WAITING,
                NOW,
                "linux_1",
                null,
                null,
                "req_1",
                "trace_1",
                null,
                NOW,
                NOW);
    }
}
