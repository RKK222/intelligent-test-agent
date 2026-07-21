package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class XxlJobExecutorLifecycleTest {

    @Test
    void waitsForAdminReadinessAndStartsExecutorExactlyOnceAfterRecovery() {
        XxlJobProperties properties = enabledProperties();
        AtomicBoolean ready = new AtomicBoolean();
        XxlJobAdminReadinessProbe probe = addresses -> ready.get();
        DeferredXxlJobSpringExecutor executor = mock(DeferredXxlJobSpringExecutor.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        XxlJobExecutorLifecycle lifecycle =
                new XxlJobExecutorLifecycle(properties, probe, executor, scheduler);

        lifecycle.start();

        ArgumentCaptor<Runnable> firstAttempt = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).execute(firstAttempt.capture());
        firstAttempt.getValue().run();
        verify(executor, never()).startAfterAdminReady();

        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(retry.capture(), eq(250L), eq(TimeUnit.MILLISECONDS));
        ready.set(true);
        retry.getValue().run();
        verify(executor, times(1)).startAfterAdminReady();
        assertThat(lifecycle.isExecutorStarted()).isTrue();

        clearInvocations(scheduler);
        retry.getValue().run();
        verify(executor, times(1)).startAfterAdminReady();
        verify(scheduler, never())
                .schedule(org.mockito.ArgumentMatchers.any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void stopBeforeAdminRecoveryNeverStartsOrDestroysUpstreamExecutor() {
        XxlJobProperties properties = enabledProperties();
        XxlJobAdminReadinessProbe probe = addresses -> false;
        DeferredXxlJobSpringExecutor executor = mock(DeferredXxlJobSpringExecutor.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        XxlJobExecutorLifecycle lifecycle =
                new XxlJobExecutorLifecycle(properties, probe, executor, scheduler);

        lifecycle.start();
        lifecycle.stop();

        verify(scheduler).shutdownNow();
        verify(executor, never()).startAfterAdminReady();
        verify(executor, never()).destroy();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void springInitializationCallbackLeavesExecutorStoppedUntilCoordinatorStartsIt() {
        DeferredXxlJobSpringExecutor executor = new DeferredXxlJobSpringExecutor();

        executor.afterSingletonsInstantiated();

        assertThat(executor.isStarted()).isFalse();
        assertThat(executor.getExecutorRegistryThreadHelper()).isNull();
        assertThatCode(executor::destroy).doesNotThrowAnyException();
    }

    private static XxlJobProperties enabledProperties() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setEnabled(true);
        properties.getExecutor().setAdminAddresses("http://127.0.0.1:18080/xxl-job-admin");
        return properties;
    }
}
