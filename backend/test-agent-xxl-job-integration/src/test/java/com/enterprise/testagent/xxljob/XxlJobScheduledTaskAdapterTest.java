package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskLock;
import com.enterprise.testagent.scheduler.ScheduledTaskLockLease;
import com.enterprise.testagent.scheduler.ScheduledTaskRegistry;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class XxlJobScheduledTaskAdapterTest {

    private static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("opencode-runtime.analytics-rollup");
    private final java.util.concurrent.ScheduledExecutorService renewalExecutor = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void closeExecutor() {
        renewalExecutor.shutdownNow();
        Thread.interrupted();
    }

    @Test
    void globalMutexSkipsSuccessfullyWhenExistingRedisLockIsHeld() {
        Fixture fixture = fixture(context -> ScheduledTaskResult.empty());
        when(fixture.lock().acquire(TASK_KEY, Duration.ofSeconds(30))).thenReturn(Optional.empty());

        XxlJobTaskExecutionOutcome outcome = fixture.adapter().execute(param("GLOBAL_MUTEX"));

        assertThat(outcome.status()).isEqualTo(XxlJobTaskExecutionStatus.SKIPPED_LOCK_HELD);
        assertThat(fixture.handlerInvoked()).isFalse();
    }

    @Test
    void allowOverlapSkipsGlobalLockAndPassesPayloadToHandler() {
        Fixture fixture = fixture(context -> {
            assertThat(context.payload()).containsEntry("scope", "all");
            return ScheduledTaskResult.of(Map.of("updated", 3));
        });

        XxlJobTaskExecutionOutcome outcome = fixture.adapter().execute("""
                {"taskKey":"opencode-runtime.analytics-rollup","concurrencyPolicy":"ALLOW_OVERLAP","payload":{"scope":"all"}}
                """);

        assertThat(outcome.status()).isEqualTo(XxlJobTaskExecutionStatus.SUCCEEDED);
        assertThat(outcome.result()).containsEntry("updated", 3);
        verify(fixture.lock(), never()).acquire(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUnknownTaskAndPolicy() {
        Fixture fixture = fixture(context -> ScheduledTaskResult.empty());
        when(fixture.registry().handlerFor(new ScheduledTaskKey("unknown.task"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.adapter().execute("""
                {"taskKey":"unknown.task","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}
                """))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("handler");
        assertThatThrownBy(() -> fixture.adapter().execute(param("LOCAL_ONLY")))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("并发策略");
    }

    @Test
    void lostRenewalFailsRunAndStopSignalIncludesThreadInterruption() {
        AtomicBoolean stopObserved = new AtomicBoolean();
        Fixture fixture = fixture(context -> {
            stopObserved.set(context.stopRequested());
            long deadline = System.nanoTime() + Duration.ofMillis(40).toNanos();
            while (System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            return ScheduledTaskResult.empty();
        });
        ScheduledTaskLockLease lease = org.mockito.Mockito.mock(ScheduledTaskLockLease.class);
        when(lease.ttl()).thenReturn(Duration.ofMillis(30));
        when(lease.renew()).thenReturn(false);
        when(fixture.lock().acquire(TASK_KEY, Duration.ofSeconds(30))).thenReturn(Optional.of(lease));

        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> fixture.adapter().execute(param("GLOBAL_MUTEX")))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("续租");
        assertThat(stopObserved).isTrue();
        verify(lease).release();
    }

    private Fixture fixture(java.util.function.Function<ScheduledTaskContext, ScheduledTaskResult> execution) {
        ScheduledTaskRegistry registry = org.mockito.Mockito.mock(ScheduledTaskRegistry.class);
        ScheduledTaskLock lock = org.mockito.Mockito.mock(ScheduledTaskLock.class);
        AtomicBoolean invoked = new AtomicBoolean();
        ScheduledTaskHandler handler = new ScheduledTaskHandler() {
            @Override
            public ScheduledTaskKey taskKey() {
                return TASK_KEY;
            }

            @Override
            public String name() {
                return "analytics";
            }

            @Override
            public String cronExpression() {
                return "0 0/5 * * * ? *";
            }

            @Override
            public Duration lockTtl() {
                return Duration.ofSeconds(30);
            }

            @Override
            public ScheduledTaskResult run(ScheduledTaskContext context) {
                invoked.set(true);
                return execution.apply(context);
            }
        };
        when(registry.handlerFor(TASK_KEY)).thenReturn(Optional.of(handler));
        XxlJobScheduledTaskAdapter adapter = new XxlJobScheduledTaskAdapter(
                registry,
                lock,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC),
                renewalExecutor,
                Duration.ofMillis(5));
        return new Fixture(adapter, registry, lock, invoked);
    }

    private static String param(String policy) {
        return """
                {"taskKey":"opencode-runtime.analytics-rollup","concurrencyPolicy":"%s","payload":{}}
                """.formatted(policy);
    }

    private record Fixture(
            XxlJobScheduledTaskAdapter adapter,
            ScheduledTaskRegistry registry,
            ScheduledTaskLock lock,
            AtomicBoolean invoked) {

        boolean handlerInvoked() {
            return invoked.get();
        }
    }
}
