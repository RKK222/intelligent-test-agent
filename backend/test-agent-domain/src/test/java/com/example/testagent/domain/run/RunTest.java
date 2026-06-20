package com.example.testagent.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RunTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-20T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-20T00:00:05Z");

    @Test
    void lifecycleHelpersPreserveIdentityAndTraceId() {
        Run run = new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.PENDING,
                CREATED_AT,
                CREATED_AT,
                "trace_123");

        Run running = run.start(UPDATED_AT);
        Run cancelling = running.requestCancel(Instant.parse("2026-06-20T00:00:06Z"));

        assertThat(running.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(running.runId()).isEqualTo(run.runId());
        assertThat(running.traceId()).isEqualTo("trace_123");
        assertThat(cancelling.status()).isEqualTo(RunStatus.CANCELLING);
    }

    @Test
    void constructorRejectsUpdatedAtBeforeCreatedAt() {
        assertThatThrownBy(() -> new Run(
                        new RunId("run_1234567890abcdef"),
                        new SessionId("ses_1234567890abcdef"),
                        new WorkspaceId("wrk_1234567890abcdef"),
                        RunStatus.PENDING,
                        UPDATED_AT,
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("updatedAt");
    }
}
