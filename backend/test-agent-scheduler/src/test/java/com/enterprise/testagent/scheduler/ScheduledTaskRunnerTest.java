package com.enterprise.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.scheduler.ScheduledTask;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRun;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ScheduledTaskRunnerTest {

    private static final Instant NOW = Instant.parse("2026-06-25T00:00:00Z");
    private static final ScheduledTaskKey PERIODIC_KEY = new ScheduledTaskKey("daily.cleanup");
    private static final ScheduledTaskKey USER_PLAN_KEY = new ScheduledTaskKey("night.user-plan-dispatch");
    private static final String TRACE_ID = "trace_scheduler_test";

    @Test
    void legacyRunnerNoLongerScansCronOrManualRuns() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        repository.saveTask(periodicTask());
        ScheduledTaskRun manual = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_manual_1234567890abcdef"),
                PERIODIC_KEY,
                null,
                ScheduledTaskTriggerType.MANUAL,
                null,
                NOW,
                TRACE_ID);
        repository.saveRun(manual);
        RecordingHandler handler = new RecordingHandler(PERIODIC_KEY, Set.of(
                ScheduledTaskTriggerType.CRON,
                ScheduledTaskTriggerType.MANUAL));

        runner(repository, List.of(handler), () -> null).scanOnce();

        assertThat(repository.allRuns()).containsExactly(manual);
        assertThat(repository.findRunById(manual.taskRunId()).orElseThrow().status())
                .isEqualTo(ScheduledTaskRunStatus.PENDING);
        assertThat(handler.invocations).hasValue(0);
    }

    @Test
    void applicationRunnerSynchronizesOnlyUserPlanHandlers() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        RecordingHandler periodic = new RecordingHandler(PERIODIC_KEY, Set.of(ScheduledTaskTriggerType.CRON));
        RecordingHandler userPlan = new RecordingHandler(USER_PLAN_KEY, Set.of(ScheduledTaskTriggerType.USER_PLAN));
        ScheduledTaskRunner runner = runner(repository, List.of(periodic, userPlan), () -> "linux-a");

        runner.run(null);

        assertThat(repository.findTaskByKey(USER_PLAN_KEY)).isPresent();
        assertThat(repository.findTaskByKey(PERIODIC_KEY)).isEmpty();
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void pendingUserPlanStillExecutesOnlyOnMatchingLinuxAffinity() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        RecordingHandler userPlan = new RecordingHandler(USER_PLAN_KEY, Set.of(ScheduledTaskTriggerType.USER_PLAN));
        ScheduledTaskRunner runner = runner(repository, List.of(userPlan), () -> "linux-a");
        repository.saveTask(userPlanTask());
        ScheduledTaskRun matching = userPlanRun("str_user_plan_matching_123456", "linux-a");
        ScheduledTaskRun remote = userPlanRun("str_user_plan_remote_12345678", "linux-b");
        repository.saveRun(matching);
        repository.saveRun(remote);

        runner.scanOnce();
        assertThat(runner.awaitUserPlanIdle(Duration.ofSeconds(2))).isTrue();

        assertThat(repository.findRunById(matching.taskRunId()).orElseThrow().status())
                .isEqualTo(ScheduledTaskRunStatus.SUCCEEDED);
        assertThat(repository.findRunById(remote.taskRunId()).orElseThrow().status())
                .isEqualTo(ScheduledTaskRunStatus.PENDING);
        assertThat(userPlan.invocations).hasValue(1);
    }

    @Test
    void runnerDoesNotAutoStartBeforeApplicationRunnersComplete() {
        ScheduledTaskRunner runner = runner(
                new InMemoryScheduledTaskRepository(),
                List.of(new RecordingHandler(USER_PLAN_KEY, Set.of(ScheduledTaskTriggerType.USER_PLAN))),
                () -> "linux-a");

        assertThat(runner.isAutoStartup()).isFalse();
    }

    private ScheduledTaskRunner runner(
            InMemoryScheduledTaskRepository repository,
            List<ScheduledTaskHandler> handlers,
            ScheduledTaskExecutionAffinityProvider affinityProvider) {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setInstanceId("scheduler-test-instance");
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        CronScheduleCalculator calculator = new CronScheduleCalculator();
        ScheduledTaskRegistry registry = new ScheduledTaskRegistry(repository, calculator, clock, handlers);
        return new ScheduledTaskRunner(
                repository,
                registry,
                new FakeScheduledTaskLock(),
                calculator,
                properties,
                clock,
                affinityProvider);
    }

    private ScheduledTask periodicTask() {
        return ScheduledTask.registered(
                        PERIODIC_KEY,
                        "Daily Cleanup",
                        "0 * * * * *",
                        Duration.ofMinutes(5),
                        NOW.minusSeconds(3600),
                        TRACE_ID)
                .withNextFireAt(NOW.minusSeconds(60), NOW.minusSeconds(3600));
    }

    private ScheduledTask userPlanTask() {
        return ScheduledTask.registered(
                USER_PLAN_KEY,
                "Night dispatch",
                null,
                Duration.ofMinutes(5),
                NOW.minusSeconds(3600),
                TRACE_ID);
    }

    private ScheduledTaskRun userPlanRun(String id, String affinity) {
        return ScheduledTaskRun.pending(
                new ScheduledTaskRunId(id),
                USER_PLAN_KEY,
                null,
                ScheduledTaskTriggerType.USER_PLAN,
                null,
                NOW,
                affinity,
                TRACE_ID);
    }

    private static final class RecordingHandler implements ScheduledTaskHandler {
        private final ScheduledTaskKey taskKey;
        private final Set<ScheduledTaskTriggerType> triggerTypes;
        private final AtomicInteger invocations = new AtomicInteger();

        private RecordingHandler(ScheduledTaskKey taskKey, Set<ScheduledTaskTriggerType> triggerTypes) {
            this.taskKey = taskKey;
            this.triggerTypes = triggerTypes;
        }

        @Override
        public ScheduledTaskKey taskKey() {
            return taskKey;
        }

        @Override
        public String name() {
            return taskKey.value();
        }

        @Override
        public String cronExpression() {
            return triggerTypes.contains(ScheduledTaskTriggerType.CRON) ? "0 * * * * *" : null;
        }

        @Override
        public Set<ScheduledTaskTriggerType> supportedTriggerTypes() {
            return triggerTypes;
        }

        @Override
        public ScheduledTaskResult run(ScheduledTaskContext context) {
            invocations.incrementAndGet();
            return ScheduledTaskResult.of(Map.of("handled", true));
        }
    }
}
