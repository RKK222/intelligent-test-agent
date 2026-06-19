package com.example.testagent.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RunStatusTest {

    @Test
    void pendingRunCanStartOrFailOrCancelDirectly() {
        assertThat(RunStatus.PENDING.canTransitionTo(RunStatus.RUNNING)).isTrue();
        assertThat(RunStatus.PENDING.canTransitionTo(RunStatus.FAILED)).isTrue();
        assertThat(RunStatus.PENDING.canTransitionTo(RunStatus.CANCELLED)).isTrue();
        assertThat(RunStatus.PENDING.canTransitionTo(RunStatus.CANCELLING)).isFalse();
    }

    @Test
    void runningRunCanEnterCancellingOrTerminalState() {
        assertThat(RunStatus.RUNNING.canTransitionTo(RunStatus.CANCELLING)).isTrue();
        assertThat(RunStatus.RUNNING.canTransitionTo(RunStatus.SUCCEEDED)).isTrue();
        assertThat(RunStatus.RUNNING.canTransitionTo(RunStatus.FAILED)).isTrue();
        assertThat(RunStatus.RUNNING.canTransitionTo(RunStatus.CANCELLED)).isFalse();
    }

    @Test
    void terminalRunCannotTransitionAgain() {
        assertThat(RunStatus.SUCCEEDED.isTerminal()).isTrue();
        assertThat(RunStatus.FAILED.isTerminal()).isTrue();
        assertThat(RunStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(RunStatus.SUCCEEDED.canTransitionTo(RunStatus.RUNNING)).isFalse();
    }

    @Test
    void pendingCancelRequestTransitionsDirectlyToCancelled() {
        Instant createdAt = Instant.parse("2026-06-19T00:00:00Z");
        Run run = new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.PENDING,
                createdAt,
                createdAt);

        Run cancelled = run.requestCancel(Instant.parse("2026-06-19T00:00:05Z"));

        assertThat(cancelled.status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(cancelled.updatedAt()).isEqualTo(Instant.parse("2026-06-19T00:00:05Z"));
    }

    @Test
    void invalidTransitionRaisesPlatformConflict() {
        Instant createdAt = Instant.parse("2026-06-19T00:00:00Z");
        Run run = new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.SUCCEEDED,
                createdAt,
                createdAt);

        assertThatThrownBy(() -> run.transitionTo(RunStatus.RUNNING, Instant.parse("2026-06-19T00:00:05Z")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }
}
