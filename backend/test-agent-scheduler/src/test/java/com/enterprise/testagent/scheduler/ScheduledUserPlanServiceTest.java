package com.enterprise.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRepository;
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
import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScheduledUserPlanServiceTest {
    @Test
    void cancelDoesNotOverwriteARunClaimedByTheRunner() {
        Instant now = Instant.parse("2026-07-18T13:00:00Z");
        ScheduledTaskRepository repository = mock(ScheduledTaskRepository.class);
        ScheduledTaskRun pending = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_night_1234567890abcdef"),
                new ScheduledTaskKey("night.test"), null, ScheduledTaskTriggerType.USER_PLAN,
                new UserId("usr_night_1234567890"), now.plusSeconds(60), "linux-a", "trace_user_plan", now);
        ScheduledTaskRun claimed = pending.start("backend-a", now.plusSeconds(61));
        when(repository.findRunById(pending.taskRunId()))
                .thenReturn(Optional.of(pending), Optional.of(claimed));
        when(repository.updateRunIfStatus(any(), org.mockito.ArgumentMatchers.eq(ScheduledTaskRunStatus.PENDING)))
                .thenReturn(false);
        ScheduledUserPlanService service = new ScheduledUserPlanService(
                repository,
                mock(ScheduledTaskRegistry.class),
                new SchedulerProperties(),
                mock(ScheduledTaskDispatcher.class),
                Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.cancelPending(pending.taskRunId(), "用户取消"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("已经开始");
        verify(repository, never()).saveRun(any());
    }

    @Test
    void createsAffinityBoundPendingRunAndCanCancelIt() {
        Instant now = Instant.parse("2026-07-18T13:00:00Z");
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        ScheduledTaskHandler handler = new ScheduledTaskHandler() {
            public ScheduledTaskKey taskKey() { return new ScheduledTaskKey("night.test"); }
            public String name() { return "Night"; }
            public String cronExpression() { return null; }
            public Set<ScheduledTaskTriggerType> supportedTriggerTypes() { return Set.of(ScheduledTaskTriggerType.USER_PLAN); }
            public ScheduledTaskResult run(ScheduledTaskContext context) { return ScheduledTaskResult.empty(); }
        };
        ScheduledTaskRegistry registry = new ScheduledTaskRegistry(
                repository, new CronScheduleCalculator(), Clock.fixed(now, ZoneOffset.UTC), List.of(handler));
        registry.syncRegisteredTasks("trace_user_plan");
        SchedulerProperties properties = new SchedulerProperties();
        properties.setEnabled(true);
        ScheduledTaskDispatcher dispatcher = new ScheduledTaskDispatcher() {
            public void wakeUp() { }
            public boolean runnerRunning() { return true; }
        };
        ScheduledUserPlanService service = new ScheduledUserPlanService(
                repository, registry, properties, dispatcher, Clock.fixed(now, ZoneOffset.UTC));

        var run = service.schedule(
                handler.taskKey(), new UserId("usr_night_1234567890"), now.plus(Duration.ofMinutes(15)),
                "linux-a", "trace_user_plan");
        var cancelled = service.cancelPending(run.taskRunId(), "用户取消");

        assertThat(run.triggerType()).isEqualTo(ScheduledTaskTriggerType.USER_PLAN);
        assertThat(run.executionAffinity()).isEqualTo("linux-a");
        assertThat(cancelled.status()).isEqualTo(ScheduledTaskRunStatus.SKIPPED);
    }

    @Test
    void bestEffortCancelOnlyTransitionsPendingUserPlans() {
        Instant now = Instant.parse("2026-07-18T13:00:00Z");
        InMemoryScheduledTaskRepository repository = new InMemoryScheduledTaskRepository();
        ScheduledTaskRun pending = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_night_best_effort"),
                new ScheduledTaskKey("night.test"), null, ScheduledTaskTriggerType.USER_PLAN,
                new UserId("usr_night_best_effort"), now.plusSeconds(60), "linux-a", "trace_user_plan", now);
        repository.saveRun(pending);
        ScheduledUserPlanService service = new ScheduledUserPlanService(
                repository,
                mock(ScheduledTaskRegistry.class),
                new SchedulerProperties(),
                mock(ScheduledTaskDispatcher.class),
                Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.cancelPendingIfPresent(pending.taskRunId(), "替换旧计划")).isTrue();
        assertThat(service.cancelPendingIfPresent(pending.taskRunId(), "重复清理")).isFalse();
        assertThat(repository.findRunById(pending.taskRunId()).orElseThrow().status())
                .isEqualTo(ScheduledTaskRunStatus.SKIPPED);
    }
}
