package com.enterprise.testagent.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
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

    @Test
    void runDefaultsToManualSourceAndCanCarryScheduledTriggerUser() {
        Run manual = new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.PENDING,
                CREATED_AT,
                CREATED_AT,
                "trace_123");

        Run scheduled = manual.withSource(
                ConversationSourceType.SCHEDULED_TASK,
                "str_1234567890abcdef",
                new UserId("usr_1234567890abcdef"));

        assertThat(manual.sourceType()).isEqualTo(ConversationSourceType.MANUAL);
        assertThat(scheduled.sourceType()).isEqualTo(ConversationSourceType.SCHEDULED_TASK);
        assertThat(scheduled.sourceRefId()).isEqualTo("str_1234567890abcdef");
        assertThat(scheduled.triggeredByUserId()).isEqualTo(new UserId("usr_1234567890abcdef"));
    }

    @Test
    void sideQuestionSourceSurvivesRunLifecycleTransitions() {
        Run sideQuestion = new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.PENDING,
                CREATED_AT,
                CREATED_AT,
                "trace_123")
                .withSource(
                        ConversationSourceType.SIDE_QUESTION,
                        "ses_main1234567890abcdef",
                        new UserId("usr_1234567890abcdef"));

        Run succeeded = sideQuestion.start(UPDATED_AT).succeed(UPDATED_AT.plusSeconds(1));

        assertThat(succeeded.sourceType()).isEqualTo(ConversationSourceType.SIDE_QUESTION);
        assertThat(succeeded.sourceRefId()).isEqualTo("ses_main1234567890abcdef");
        assertThat(succeeded.triggeredByUserId()).isEqualTo(new UserId("usr_1234567890abcdef"));
    }

    @Test
    void terminalFactCanCorrectEarlierTerminalStatusWithoutMovingUpdatedAtBackward() {
        Run failed = new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.FAILED,
                CREATED_AT,
                UPDATED_AT,
                "trace_123");

        Run succeeded = failed.applyTerminalFact(RunStatus.SUCCEEDED, CREATED_AT.plusSeconds(1));

        assertThat(succeeded.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(succeeded.updatedAt()).isEqualTo(UPDATED_AT);
        assertThatThrownBy(() -> failed.applyTerminalFact(RunStatus.RUNNING, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminalStatus");
    }
}
