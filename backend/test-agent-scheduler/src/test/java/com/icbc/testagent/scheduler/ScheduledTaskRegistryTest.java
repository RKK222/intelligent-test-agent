package com.icbc.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduledTaskRegistryTest {

    private static final Instant NOW = Instant.parse("2026-06-25T00:00:00Z");
    private static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("daily.cleanup");

    @Test
    void syncRegistersNewHandlerAndComputesNextFireAt() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        ScheduledTaskRegistry registry = registry(repository, new FakeHandler(TASK_KEY, "Daily Cleanup", "0 0 2 * * *"));

        registry.syncRegisteredTasks("trace_scheduler_test");

        ScheduledTask task = repository.findTaskByKey(TASK_KEY).orElseThrow();
        assertThat(task.name()).isEqualTo("Daily Cleanup");
        assertThat(task.cronExpression()).isEqualTo("0 0 2 * * *");
        assertThat(task.lockTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(task.nextFireAt()).isEqualTo(Instant.parse("2026-06-25T02:00:00Z"));
    }

    @Test
    void syncPreservesAdminScheduleOverridesForExistingTask() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        ScheduledTask existing = ScheduledTask.registered(
                        TASK_KEY,
                        "Old Name",
                        "0 30 1 * * *",
                        Duration.ofMinutes(7),
                        NOW.minusSeconds(3600),
                        "trace_existing")
                .withAdminSchedule(false, "0 30 1 * * *", Duration.ofMinutes(7), NOW.minusSeconds(1800));
        repository.saveTask(existing);
        ScheduledTaskRegistry registry = registry(repository, new FakeHandler(TASK_KEY, "New Code Name", "0 0 2 * * *"));

        registry.syncRegisteredTasks("trace_scheduler_test");

        ScheduledTask task = repository.findTaskByKey(TASK_KEY).orElseThrow();
        assertThat(task.name()).isEqualTo("New Code Name");
        assertThat(task.enabled()).isFalse();
        assertThat(task.cronExpression()).isEqualTo("0 30 1 * * *");
        assertThat(task.lockTtl()).isEqualTo(Duration.ofMinutes(7));
    }

    private ScheduledTaskRegistry registry(InMemoryScheduledTaskRepository repository, ScheduledTaskHandler handler) {
        return new ScheduledTaskRegistry(
                repository,
                new CronScheduleCalculator(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                List.of(handler));
    }

    private record FakeHandler(ScheduledTaskKey taskKey, String name, String cronExpression) implements ScheduledTaskHandler {
        @Override
        public ScheduledTaskResult run(ScheduledTaskContext context) {
            return ScheduledTaskResult.empty();
        }
    }
}
