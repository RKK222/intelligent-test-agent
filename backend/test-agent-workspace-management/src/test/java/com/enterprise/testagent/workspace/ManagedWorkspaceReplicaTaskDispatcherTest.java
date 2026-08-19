package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ManagedWorkspaceReplicaTaskDispatcherTest {

    private ManagedWorkspaceReplicaTaskDispatcher dispatcher;

    @AfterEach
    void stopDispatcher() {
        if (dispatcher != null) {
            dispatcher.stop();
        }
    }

    @Test
    void boundsPendingTasksAndMergesDuplicateRepositoryVersionGroup() throws Exception {
        dispatcher = new ManagedWorkspaceReplicaTaskDispatcher(1, 2);
        dispatcher.start();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        AtomicInteger executions = new AtomicInteger();

        assertThat(dispatcher.dispatch("app:repo:20260819:main", "trace_first", () -> {
            executions.incrementAndGet();
            firstStarted.countDown();
            try {
                releaseFirst.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                completed.countDown();
            }
        })).isTrue();
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(dispatcher.dispatch(
                "app:repo:20260819:main",
                "trace_duplicate",
                executions::incrementAndGet)).isTrue();
        assertThat(dispatcher.dispatch("app:repo-2:20260819:main", "trace_second", () -> {
            executions.incrementAndGet();
            completed.countDown();
        })).isTrue();
        assertThat(dispatcher.dispatch(
                "app:repo-3:20260819:main",
                "trace_rejected",
                executions::incrementAndGet)).isFalse();

        releaseFirst.countDown();
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executions).hasValue(2);
    }
}
