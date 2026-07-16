package com.enterprise.testagent.domain.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchedulerDomainTest {

    private static final Instant NOW = Instant.parse("2026-06-25T00:00:00Z");

    @Test
    void taskDefinitionNormalizesKeyAndPreservesAdminOverrides() {
        ScheduledTask task = ScheduledTask.registered(
                new ScheduledTaskKey(" DAILY.cleanup "),
                "每日清理",
                "0 0 2 * * *",
                Duration.ofMinutes(5),
                NOW,
                "trace_1234567890abcdef");

        ScheduledTask updated = task.withAdminSchedule(false, "0 30 3 * * *", Duration.ofMinutes(10), NOW.plusSeconds(1));

        assertThat(task.taskKey().value()).isEqualTo("daily.cleanup");
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.cronExpression()).isEqualTo("0 30 3 * * *");
        assertThat(updated.lockTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(updated.registrationStatus()).isEqualTo(ScheduledTaskRegistrationStatus.REGISTERED);
    }

    @Test
    void taskRunRecordsTerminalResultAndSkipReason() {
        ScheduledTaskRun run = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_1234567890abcdef"),
                new ScheduledTaskKey("daily.cleanup"),
                null,
                ScheduledTaskTriggerType.CRON,
                null,
                NOW,
                "trace_1234567890abcdef");

        ScheduledTaskRun running = run.start("instance-a", NOW.plusSeconds(1));
        ScheduledTaskRun skipped = run.skip("LOCK_NOT_ACQUIRED", NOW.plusSeconds(2));
        ScheduledTaskRun succeeded = running.succeed(Map.of("deleted", 3), NOW.plusSeconds(3));
        ScheduledTaskRun stopping = running.requestStop(
                new UserId("usr_admin_1234567890"),
                "管理员手工停止",
                NOW.plusSeconds(2));
        ScheduledTaskRun manuallyStopped = stopping.manuallyStopped(NOW.plusSeconds(3));

        assertThat(running.status()).isEqualTo(ScheduledTaskRunStatus.RUNNING);
        assertThat(skipped.status()).isEqualTo(ScheduledTaskRunStatus.SKIPPED);
        assertThat(skipped.skipReason()).isEqualTo("LOCK_NOT_ACQUIRED");
        assertThat(succeeded.status()).isEqualTo(ScheduledTaskRunStatus.SUCCEEDED);
        assertThat(succeeded.result()).containsEntry("deleted", 3);
        assertThat(stopping.status()).isEqualTo(ScheduledTaskRunStatus.STOPPING);
        assertThat(stopping.stopRequestedByUserId()).isEqualTo(new UserId("usr_admin_1234567890"));
        assertThat(stopping.stopReason()).isEqualTo("管理员手工停止");
        assertThat(manuallyStopped.status()).isEqualTo(ScheduledTaskRunStatus.MANUALLY_STOPPED);
        assertThat(manuallyStopped.endedAt()).isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void userCronPlanKeepsOwnerAndPayloadForFutureConversationScheduler() {
        ScheduledTaskPlan plan = new ScheduledTaskPlan(
                new ScheduledTaskPlanId("stp_1234567890abcdef"),
                new ScheduledTaskKey("conversation.reminder"),
                new UserId("usr_1234567890abcdef"),
                "0 0 9 * * MON-FRI",
                Map.of("workspaceId", "wrk_1234567890abcdef"),
                true,
                NOW.plusSeconds(60),
                NOW,
                NOW,
                "trace_1234567890abcdef");

        assertThat(plan.ownerUserId()).isEqualTo(new UserId("usr_1234567890abcdef"));
        assertThat(plan.payload()).containsEntry("workspaceId", "wrk_1234567890abcdef");
        assertThat(plan.enabled()).isTrue();
        assertThatThrownBy(() -> plan.payload().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void scheduledTaskKeyRejectsUnsafeValues() {
        assertThatThrownBy(() -> new ScheduledTaskKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskKey");
        assertThatThrownBy(() -> new ScheduledTaskKey("bad/key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskKey");
    }
}
