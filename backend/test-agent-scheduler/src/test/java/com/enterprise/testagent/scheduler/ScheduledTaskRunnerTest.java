package com.enterprise.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.scheduler.ScheduledTask;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRun;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ScheduledTaskRunnerTest {

    private static final Instant NOW = Instant.parse("2026-06-25T00:00:00Z");
    private static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("daily.cleanup");
    private static final String TRACE_ID = "trace_scheduler_test";

    @Test
    void dueCronTaskRunsOnceAndAdvancesNextFireAt() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        repository.saveTask(dueTask());
        FakeScheduledTaskLock lock = new FakeScheduledTaskLock();
        RecordingHandler handler = new RecordingHandler(false);
        ScheduledTaskRunner runner = runner(repository, lock, handler);

        runner.scanOnce();

        ScheduledTaskRun run = repository.allRuns().getFirst();
        assertThat(run.status()).isEqualTo(ScheduledTaskRunStatus.SUCCEEDED);
        assertThat(run.triggerType()).isEqualTo(ScheduledTaskTriggerType.CRON);
        assertThat(run.ownerInstanceId()).isEqualTo("scheduler-test-instance");
        assertThat(run.result()).containsEntry("handled", true);
        assertThat(repository.findTaskByKey(TASK_KEY).orElseThrow().nextFireAt()).isAfter(NOW);
        assertThat(handler.invocations).hasValue(1);
        assertThat(lock.releaseCount).isEqualTo(1);
    }

    @Test
    void runnerDoesNotAutoStartBeforeApplicationRunnersComplete() {
        ScheduledTaskRunner runner = runner(
                new InMemoryScheduledTaskRepository(),
                new FakeScheduledTaskLock(),
                new RecordingHandler(false));

        assertThat(runner.isAutoStartup()).isFalse();
    }

    @Test
    void applicationRunnerSynchronizesRegisteredTasksEvenWhenSchedulerDisabled() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        ScheduledTaskRunner runner = runner(
                repository,
                new FakeScheduledTaskLock(),
                new RecordingHandler(false));

        runner.run(null);

        assertThat(repository.findTaskByKey(TASK_KEY)).isPresent();
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void overlappingCronTriggerWritesSkippedRunWithoutCallingHandler() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        repository.saveTask(dueTask());
        ScheduledTaskRun active = ScheduledTaskRun.pending(
                        new ScheduledTaskRunId("str_active_1234567890abcdef"),
                        TASK_KEY,
                        null,
                        ScheduledTaskTriggerType.CRON,
                        null,
                        NOW.minusSeconds(120),
                        TRACE_ID)
                .start("other-instance", NOW.minusSeconds(60));
        repository.saveRun(active);
        FakeScheduledTaskLock lock = new FakeScheduledTaskLock();
        RecordingHandler handler = new RecordingHandler(false);

        runner(repository, lock, handler).scanOnce();

        ScheduledTaskRun skipped = repository.allRuns().stream()
                .filter(run -> run.status() == ScheduledTaskRunStatus.SKIPPED)
                .findFirst()
                .orElseThrow();
        assertThat(skipped.skipReason()).contains("已有未结束运行");
        assertThat(handler.invocations).hasValue(0);
        assertThat(lock.acquireCount).isZero();
    }

    @Test
    void redisLockFailureWritesSkippedRunForCronTrigger() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        repository.saveTask(dueTask());
        FakeScheduledTaskLock lock = new FakeScheduledTaskLock();
        lock.acquire = false;

        runner(repository, lock, new RecordingHandler(false)).scanOnce();

        ScheduledTaskRun run = repository.allRuns().getFirst();
        assertThat(run.status()).isEqualTo(ScheduledTaskRunStatus.SKIPPED);
        assertThat(run.skipReason()).contains("Redis");
    }

    @Test
    void handlerExceptionWritesFailedRun() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        repository.saveTask(dueTask());

        runner(repository, new FakeScheduledTaskLock(), new RecordingHandler(true)).scanOnce();

        ScheduledTaskRun run = repository.allRuns().getFirst();
        assertThat(run.status()).isEqualTo(ScheduledTaskRunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void pendingManualRunIsExecutedByRunner() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        repository.saveTask(dueTask().withNextFireAt(NOW.plusSeconds(3600), NOW));
        ScheduledTaskRun manualRun = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_manual_1234567890abcdef"),
                TASK_KEY,
                null,
                ScheduledTaskTriggerType.MANUAL,
                null,
                NOW,
                TRACE_ID);
        repository.saveRun(manualRun);

        runner(repository, new FakeScheduledTaskLock(), new RecordingHandler(false)).scanOnce();

        assertThat(repository.findRunById(manualRun.taskRunId()).orElseThrow().status())
                .isEqualTo(ScheduledTaskRunStatus.SUCCEEDED);
    }

    @Test
    void pendingUserPlanRunExecutesOnlyOnMatchingAffinityAndUsesRunLevelConcurrency() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        ScheduledTaskRun matching = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_user_plan_matching_123456"),
                TASK_KEY,
                null,
                ScheduledTaskTriggerType.USER_PLAN,
                null,
                NOW,
                "linux-a",
                TRACE_ID);
        ScheduledTaskRun remote = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_user_plan_remote_12345678"),
                TASK_KEY,
                null,
                ScheduledTaskTriggerType.USER_PLAN,
                null,
                NOW,
                "linux-b",
                TRACE_ID);
        repository.saveRun(matching);
        repository.saveRun(remote);
        RecordingHandler handler = new RecordingHandler(false) {
            @Override
            public java.util.Set<ScheduledTaskTriggerType> supportedTriggerTypes() {
                return java.util.Set.of(ScheduledTaskTriggerType.USER_PLAN);
            }
        };
        ScheduledTaskRunner runner = runner(repository, new FakeScheduledTaskLock(), handler, () -> "linux-a");

        runner.scanOnce();
        runner.awaitUserPlanIdle(Duration.ofSeconds(2));

        assertThat(repository.findRunById(matching.taskRunId()).orElseThrow().status())
                .isEqualTo(ScheduledTaskRunStatus.SUCCEEDED);
        assertThat(repository.findRunById(remote.taskRunId()).orElseThrow().status())
                .isEqualTo(ScheduledTaskRunStatus.PENDING);
    }

    @Test
    void handlerCanObserveStopRequestAndRunnerRecordsManualStop() {
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        repository.saveTask(dueTask().withNextFireAt(NOW.plusSeconds(3600), NOW));
        ScheduledTaskRun manualRun = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_manual_stop_1234567890abcdef"),
                TASK_KEY,
                null,
                ScheduledTaskTriggerType.MANUAL,
                null,
                NOW,
                TRACE_ID);
        repository.saveRun(manualRun);

        runner(repository, new FakeScheduledTaskLock(), new StopAwareHandler(repository)).scanOnce();

        ScheduledTaskRun stopped = repository.findRunById(manualRun.taskRunId()).orElseThrow();
        assertThat(stopped.status()).isEqualTo(ScheduledTaskRunStatus.MANUALLY_STOPPED);
        assertThat(stopped.stopRequestedByUserId()).isEqualTo(new UserId("usr_operator_1234567890"));
        assertThat(stopped.endedAt()).isEqualTo(NOW);
    }

    private ScheduledTaskRunner runner(
            InMemoryScheduledTaskRepository repository,
            FakeScheduledTaskLock lock,
            ScheduledTaskHandler handler) {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setInstanceId("scheduler-test-instance");
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        CronScheduleCalculator calculator = new CronScheduleCalculator();
        ScheduledTaskRegistry registry = new ScheduledTaskRegistry(repository, calculator, clock, List.of(handler));
        return new ScheduledTaskRunner(repository, registry, lock, calculator, properties, clock);
    }

    private ScheduledTaskRunner runner(
            InMemoryScheduledTaskRepository repository,
            FakeScheduledTaskLock lock,
            ScheduledTaskHandler handler,
            ScheduledTaskExecutionAffinityProvider affinityProvider) {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setInstanceId("scheduler-test-instance");
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        CronScheduleCalculator calculator = new CronScheduleCalculator();
        ScheduledTaskRegistry registry = new ScheduledTaskRegistry(repository, calculator, clock, List.of(handler));
        registry.syncRegisteredTasks(TRACE_ID);
        return new ScheduledTaskRunner(repository, registry, lock, calculator, properties, clock, affinityProvider);
    }

    private ScheduledTask dueTask() {
        return ScheduledTask.registered(
                        TASK_KEY,
                        "Daily Cleanup",
                        "0 * * * * *",
                        Duration.ofMinutes(5),
                        NOW.minusSeconds(3600),
                        TRACE_ID)
                .withNextFireAt(NOW.minusSeconds(60), NOW.minusSeconds(3600));
    }

    private static class RecordingHandler implements ScheduledTaskHandler {
        private static final AtomicInteger invocations = new AtomicInteger();
        private final boolean fail;

        private RecordingHandler(boolean fail) {
            this.fail = fail;
            invocations.set(0);
        }

        @Override
        public ScheduledTaskKey taskKey() {
            return TASK_KEY;
        }

        @Override
        public String name() {
            return "Daily Cleanup";
        }

        @Override
        public String cronExpression() {
            return "0 * * * * *";
        }

        @Override
        public ScheduledTaskResult run(ScheduledTaskContext context) {
            invocations.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("boom");
            }
            return ScheduledTaskResult.of(Map.of("handled", true));
        }
    }

    private record StopAwareHandler(InMemoryScheduledTaskRepository repository) implements ScheduledTaskHandler {

        @Override
        public ScheduledTaskKey taskKey() {
            return TASK_KEY;
        }

        @Override
        public String name() {
            return "Daily Cleanup";
        }

        @Override
        public String cronExpression() {
            return "0 * * * * *";
        }

        @Override
        public ScheduledTaskResult run(ScheduledTaskContext context) {
            ScheduledTaskRun running = repository.findRunById(context.taskRunId()).orElseThrow();
            repository.saveRun(running.requestStop(
                    new UserId("usr_operator_1234567890"),
                    "管理员手工停止",
                    NOW));
            assertThat(context.stopRequested()).isTrue();
            context.throwIfStopRequested();
            return ScheduledTaskResult.of(Map.of("handled", true));
        }
    }
}
