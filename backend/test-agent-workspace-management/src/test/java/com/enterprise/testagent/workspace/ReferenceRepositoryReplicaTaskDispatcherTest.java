package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReferenceRepositoryReplicaTaskDispatcherTest {

    private ReferenceRepositoryReplicaTaskDispatcher dispatcher;

    @AfterEach
    void stopDispatcher() {
        if (dispatcher != null) {
            dispatcher.stop();
        }
    }

    @Test
    void dispatchesImmediateTaskWithoutBlockingCaller() throws Exception {
        dispatcher = new ReferenceRepositoryReplicaTaskDispatcher(1, 16, Clock.systemUTC());
        dispatcher.start();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        assertThat(dispatcher.dispatchNow(
                        new CodeRepositoryId("repo_a"),
                        1L,
                        "trace_immediate",
                        ReferenceRepositoryReplicaTaskDispatcher.WakeSource.LOCAL_REQUEST,
                        () -> {
                            entered.countDown();
                            await(release);
                        }))
                .isTrue();

        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        release.countDown();
    }

    @Test
    void deduplicatesSameRepositoryGenerationWhileTaskIsPending() throws Exception {
        dispatcher = new ReferenceRepositoryReplicaTaskDispatcher(1, 16, Clock.systemUTC());
        dispatcher.start();
        CountDownLatch blockerEntered = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch duplicateRan = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        dispatcher.dispatchNow(
                new CodeRepositoryId("repo_blocker"), 1L, "trace_blocker",
                ReferenceRepositoryReplicaTaskDispatcher.WakeSource.LOCAL_REQUEST,
                () -> {
                    blockerEntered.countDown();
                    await(releaseBlocker);
                });
        assertThat(blockerEntered.await(1, TimeUnit.SECONDS)).isTrue();

        Runnable duplicate = () -> {
            executions.incrementAndGet();
            duplicateRan.countDown();
        };
        assertThat(dispatcher.dispatchNow(
                        new CodeRepositoryId("repo_a"), 2L, "trace_first",
                        ReferenceRepositoryReplicaTaskDispatcher.WakeSource.SERVER_BROADCAST, duplicate))
                .isTrue();
        assertThat(dispatcher.dispatchNow(
                        new CodeRepositoryId("repo_a"), 2L, "trace_duplicate",
                        ReferenceRepositoryReplicaTaskDispatcher.WakeSource.RECONCILIATION, duplicate))
                .isTrue();

        releaseBlocker.countDown();
        assertThat(duplicateRan.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(executions).hasValue(1);
    }

    @Test
    void earlierImmediateWakeupReplacesDelayedTask() throws Exception {
        dispatcher = new ReferenceRepositoryReplicaTaskDispatcher(1, 16, Clock.systemUTC());
        dispatcher.start();
        AtomicInteger delayedExecutions = new AtomicInteger();
        CountDownLatch immediateRan = new CountDownLatch(1);
        CodeRepositoryId repositoryId = new CodeRepositoryId("repo_a");
        assertThat(dispatcher.dispatchAt(
                        repositoryId,
                        3L,
                        "trace_delayed",
                        ReferenceRepositoryReplicaTaskDispatcher.WakeSource.RETRY,
                        Instant.now().plusSeconds(30),
                        delayedExecutions::incrementAndGet))
                .isTrue();

        assertThat(dispatcher.dispatchNow(
                        repositoryId,
                        3L,
                        "trace_now",
                        ReferenceRepositoryReplicaTaskDispatcher.WakeSource.SERVER_BROADCAST,
                        immediateRan::countDown))
                .isTrue();

        assertThat(immediateRan.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(delayedExecutions).hasValue(0);
    }

    @Test
    void limitsConcurrentTasksToConfiguredWorkerCount() throws Exception {
        dispatcher = new ReferenceRepositoryReplicaTaskDispatcher(2, 16, Clock.systemUTC());
        dispatcher.start();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(3);

        for (int index = 0; index < 3; index++) {
            dispatcher.dispatchNow(
                    new CodeRepositoryId("repo_" + index),
                    1L,
                    "trace_concurrency",
                    ReferenceRepositoryReplicaTaskDispatcher.WakeSource.LOCAL_REQUEST,
                    () -> {
                        int current = active.incrementAndGet();
                        maximum.accumulateAndGet(current, Math::max);
                        bothEntered.countDown();
                        await(release);
                        active.decrementAndGet();
                        completed.countDown();
                    });
        }

        assertThat(bothEntered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(maximum).hasValue(2);
        release.countDown();
        assertThat(completed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(maximum).hasValue(2);
    }

    @Test
    void rejectsNewKeyAtCapacityWithoutRunningOnCallerThread() throws Exception {
        dispatcher = new ReferenceRepositoryReplicaTaskDispatcher(1, 1, Clock.systemUTC());
        dispatcher.start();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger rejectedExecutions = new AtomicInteger();
        dispatcher.dispatchNow(
                new CodeRepositoryId("repo_a"), 1L, "trace_capacity",
                ReferenceRepositoryReplicaTaskDispatcher.WakeSource.LOCAL_REQUEST,
                () -> {
                    entered.countDown();
                    await(release);
                });
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        assertThat(dispatcher.dispatchNow(
                        new CodeRepositoryId("repo_b"), 1L, "trace_rejected",
                        ReferenceRepositoryReplicaTaskDispatcher.WakeSource.SERVER_BROADCAST,
                        rejectedExecutions::incrementAndGet))
                .isFalse();
        assertThat(rejectedExecutions).hasValue(0);
        release.countDown();
    }

    @Test
    void schedulesRetryAtRequestedInstantAndRejectsAfterStop() throws Exception {
        dispatcher = new ReferenceRepositoryReplicaTaskDispatcher(1, 16, Clock.systemUTC());
        dispatcher.start();
        CountDownLatch ran = new CountDownLatch(1);
        Instant started = Instant.now();

        assertThat(dispatcher.dispatchAt(
                        new CodeRepositoryId("repo_a"),
                        4L,
                        "trace_retry",
                        ReferenceRepositoryReplicaTaskDispatcher.WakeSource.RETRY,
                        started.plusMillis(100),
                        ran::countDown))
                .isTrue();
        assertThat(ran.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(Duration.between(started, Instant.now())).isGreaterThanOrEqualTo(Duration.ofMillis(60));

        dispatcher.stop();
        assertThat(dispatcher.dispatchNow(
                        new CodeRepositoryId("repo_b"),
                        1L,
                        "trace_stopped",
                        ReferenceRepositoryReplicaTaskDispatcher.WakeSource.LOCAL_REQUEST,
                        () -> { }))
                .isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
