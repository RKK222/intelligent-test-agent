package com.icbc.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRun;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SchedulerManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-25T00:00:00Z");
    private static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("daily.cleanup");

    @Test
    void updateTaskValidatesCronAndRecomputesNextFireAt() {
        InMemoryScheduledTaskRepository repository = repositoryWithTask();
        SchedulerManagementService service = service(repository, new RecordingDispatcher());

        ScheduledTask updated = service.updateTask(
                TASK_KEY,
                new ScheduledTaskUpdateCommand(false, "0 0 3 * * *", Duration.ofMinutes(10)),
                "trace_scheduler_test");

        assertThat(updated.enabled()).isFalse();
        assertThat(updated.cronExpression()).isEqualTo("0 0 3 * * *");
        assertThat(updated.lockTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(updated.nextFireAt()).isEqualTo(Instant.parse("2026-06-25T03:00:00Z"));
    }

    @Test
    void invalidCronUsesUnifiedPlatformException() {
        SchedulerManagementService service = service(repositoryWithTask(), new RecordingDispatcher());

        assertThatThrownBy(() -> service.updateTask(
                        TASK_KEY,
                        new ScheduledTaskUpdateCommand(null, "bad cron", null),
                        "trace_scheduler_test"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("Cron 表达式无效");
    }

    @Test
    void triggerCreatesPendingManualRunAndWakesDispatcher() {
        InMemoryScheduledTaskRepository repository = repositoryWithTask();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        SchedulerManagementService service = service(repository, dispatcher);

        ScheduledTaskRun run = service.trigger(
                TASK_KEY,
                new UserId("usr_admin_1234567890"),
                "trace_scheduler_test");

        assertThat(run.status()).isEqualTo(ScheduledTaskRunStatus.PENDING);
        assertThat(run.triggerType()).isEqualTo(ScheduledTaskTriggerType.MANUAL);
        assertThat(run.requestedByUserId()).isEqualTo(new UserId("usr_admin_1234567890"));
        assertThat(dispatcher.wakeUps).isEqualTo(1);
    }

    private SchedulerManagementService service(InMemoryScheduledTaskRepository repository, RecordingDispatcher dispatcher) {
        return new SchedulerManagementService(
                repository,
                new CronScheduleCalculator(),
                dispatcher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private InMemoryScheduledTaskRepository repositoryWithTask() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        repository.saveTask(ScheduledTask.registered(
                        TASK_KEY,
                        "Daily Cleanup",
                        "0 0 2 * * *",
                        Duration.ofMinutes(5),
                        NOW.minusSeconds(3600),
                        "trace_scheduler_test")
                .withNextFireAt(Instant.parse("2026-06-25T02:00:00Z"), NOW.minusSeconds(3600)));
        return repository;
    }

    private static final class RecordingDispatcher implements ScheduledTaskDispatcher {
        private int wakeUps;

        @Override
        public void wakeUp() {
            wakeUps++;
        }
    }
}
